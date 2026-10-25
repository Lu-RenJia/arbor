<script setup lang="ts">
/**
 * 应用执行结果面板（我的应用 / 应用广场共用）
 * 负责执行中、停止、失败、空结果以及 表格 / JSON / 文本 三种结果视图。
 * 视图类型与表头开关由父组件受控传入，保证重复执行/收起展开后不丢失。
 */
import { computed } from 'vue'
import { NButton, NSpace, NRadioGroup, NRadioButton, NCheckbox, useMessage } from 'naive-ui'
import { downloadFileById } from '../api/file'
import { normalizeResult, toPasteText, textStats } from '../utils/resultExport'
import MappedResultTable from './MappedResultTable.vue'

const props = defineProps<{
  /** 执行结果；undefined 表示尚未产生结果 */
  result: any
  /** 是否执行中 */
  executing: boolean
  /** 执行错误信息 */
  error: string
  /** 所属流程ID（映射表格用） */
  flowId: number | null
  /** 当前结果视图 */
  viewMode: 'table' | 'json' | 'text'
  /** 文本视图是否输出表头 */
  header: boolean
}>()

const emit = defineEmits<{
  (e: 'update:viewMode', value: 'table' | 'json' | 'text'): void
  (e: 'update:header', value: boolean): void
  (e: 'cancel'): void
}>()

const message = useMessage()

/** 文本视图的粘贴文本：显示与复制用同一份内容 */
const pasteText = computed(() => props.viewMode === 'text' ? toPasteText(props.result?.data, props.header) : '')
const stats = computed(() => props.viewMode === 'text' ? textStats(pasteText.value) : null)
/** 标量数组/纯文本没有表头概念，不显示“含表头” */
const hasHeaderOption = computed(() => normalizeResult(props.result?.data).columns.length > 0)

function downloadOutputFile() {
  const file = props.result?.outputFile
  if (file) downloadFileById(file.fileId)
}

/** 复制当前视图可见的内容：文本视图复制粘贴文本，JSON 视图复制显示的 JSON */
async function copyResult() {
  const r = props.result
  if (!r) return
  const text = props.viewMode === 'json'
    ? JSON.stringify(r.data ?? null, null, 2)
    : pasteText.value
  if (!text) { message.warning('没有可复制的内容'); return }
  try { await navigator.clipboard.writeText(text); message.success('已复制') } catch { message.error('复制失败') }
}
</script>

<template>
  <!-- 执行中 -->
  <div v-if="executing" style="padding:24px;text-align:center">
    <span class="app-exec-spinner" />
    <span style="color:#999;font-size:13px;vertical-align:middle;margin-right:12px">执行中…</span>
    <NButton size="tiny" type="error" @click="emit('cancel')">取消执行</NButton>
  </div>

  <!-- 有结果 -->
  <div v-else-if="result !== undefined" style="padding:12px 16px">
    <!-- 流程停止 -->
    <template v-if="result.stopped">
      <NSpace align="center" :size="12">
        <span style="font-weight:600;color:#faad14">⏹ 流程停止</span>
        <span v-if="result.duration != null" style="font-size:11px;color:#999">耗时 {{ result.duration }}ms</span>
        <span v-if="result.nodeCount != null" style="font-size:11px;color:#999">{{ result.nodeCount }} 节点</span>
      </NSpace>
      <div style="margin-top:8px;padding:8px 12px;background:#fffbe6;border:1px solid #ffe58f;border-radius:4px;font-size:13px;color:#ad6800">
        {{ result.data || '流程已停止' }}
      </div>
    </template>

    <template v-else>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
        <NSpace align="center" :size="12">
          <span :style="{ fontWeight: '600', color: result.success ? '#52c41a' : '#ff4d4f' }">
            {{ result.success ? '✓ 成功' : '✗ 失败' }}
          </span>
          <span v-if="result.duration != null" style="font-size:11px;color:#999">耗时 {{ result.duration }}ms</span>
          <span v-if="result.nodeCount != null" style="font-size:11px;color:#999">{{ result.nodeCount }} 节点</span>
          <NButton v-if="result.outputFile" size="tiny" type="info" @click="downloadOutputFile">
            📥 {{ result.outputFile.fileName }}{{ result.outputFile.sizeText ? ` (${result.outputFile.sizeText})` : '' }}
          </NButton>
        </NSpace>
        <NSpace align="center" :size="8">
          <NRadioGroup :value="viewMode" size="small" @update:value="(v: any) => emit('update:viewMode', v)">
            <NRadioButton value="table">表格</NRadioButton>
            <NRadioButton value="json">JSON</NRadioButton>
            <NRadioButton value="text">文本</NRadioButton>
          </NRadioGroup>
          <NCheckbox
            v-if="viewMode === 'text' && hasHeaderOption"
            :checked="header"
            size="small"
            @update:checked="(v: boolean) => emit('update:header', v)"
          >含表头</NCheckbox>
          <NButton
            v-if="viewMode !== 'table'"
            size="tiny"
            :disabled="viewMode === 'text' && !pasteText"
            @click="copyResult"
          >📋 复制</NButton>
          <span v-if="stats" style="font-size:11px;color:#999">{{ stats.lines }} 行 · {{ stats.chars }} 字符</span>
        </NSpace>
      </div>

      <textarea
        v-if="viewMode === 'text' && pasteText"
        readonly
        :value="pasteText"
        wrap="off"
        spellcheck="false"
        style="width:100%; min-height:120px; max-height:300px; resize:vertical; border:1px solid #d9d9d9; border-radius:4px; padding:8px; font-size:12px; font-family:monospace; line-height:1.5; background:#fff; box-sizing:border-box; white-space:pre; overflow:auto"
      />
      <div v-else-if="viewMode === 'text'" style="padding:16px;text-align:center;color:#999;font-size:12px">暂无数据</div>
      <MappedResultTable v-else-if="viewMode === 'table'" :flow-id="flowId ?? 0" :data="result.data" style="overflow-x:auto" />
      <pre v-else style="margin:0;background:#fff;padding:8px;border-radius:4px;font-size:12px;max-height:300px;overflow:auto">{{ JSON.stringify(result.data ?? null, null, 2) }}</pre>
    </template>
  </div>

  <!-- 执行失败 -->
  <div v-else-if="error" style="padding:12px 16px">
    <span style="color:#ff4d4f;font-size:12px">{{ error }}</span>
  </div>

  <!-- 暂无结果 -->
  <div v-else style="padding:12px 16px;color:#999;font-size:12px">暂无结果</div>
</template>

<style>
.app-exec-spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 3px solid #e8e8e8;
  border-top-color: #18a058;
  border-radius: 50%;
  animation: arbor-spin 0.8s linear infinite;
  vertical-align: middle;
  margin-right: 8px;
}
@keyframes arbor-spin {
  to { transform: rotate(360deg); }
}
</style>
