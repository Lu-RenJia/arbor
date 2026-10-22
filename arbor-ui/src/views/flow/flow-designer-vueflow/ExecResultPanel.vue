<script setup lang="ts">
/**
 * 底部执行结果面板：可拖拽伸缩 / 收起展开 / 概览·日志·JSON·映射四视图。
 * 点击表格行通过 emit 定位节点；保存结果示例由父组件回写 exampleDataMap。
 */
import { ref, computed, watch, h } from 'vue'
import { NButton, NSpace, NTag, NRadioGroup, NRadioButton, NDataTable, NCheckbox, useMessage } from 'naive-ui'
import { formatDateTime, formatDuration } from '../../../utils/format'
import { normalizeResult, toPasteText, textStats, rowsToPasteText } from '../../../utils/resultExport'
import { downloadFileById } from '../../../api/file'
import MappedResultTable from '../../../components/MappedResultTable.vue'
import LogConsole from '../../../components/LogConsole.vue'
import { NODE_TYPE_LABELS } from './useFlowGraph'

const props = defineProps<{
  execResult: any
  executing: boolean
  /** 是否为 localStorage 恢复的历史数据（高亮提示） */
  isHistory: boolean
  savingExample: boolean
  flowId: number
}>()

const emit = defineEmits<{ (e: 'select-node', nodeCode: string): void; (e: 'save-examples'): void }>()

const message = useMessage()
const execPanelCollapsed = ref(false)
const execViewMode = ref<'table' | 'log' | 'json' | 'mapped' | 'text'>('table')
/** 文本视图是否输出表头（仅对象表格有效） */
const textHeader = ref(true)
/** 日志/映射视图的复制入口（复制其当前可见内容） */
const logRef = ref<any>(null)
const mappedRef = ref<any>(null)

/** 最后一个成功节点的原始数据（映射 / 文本视图共用） */
const finalNodeData = computed(() => {
  const list = props.execResult?.nodeDetailList
  if (!list) return null
  for (let i = list.length - 1; i >= 0; i--) {
    const node = list[i]
    if (node.success && node.data != null) return node.data
  }
  return null
})

/** 文本视图的粘贴文本：显示与复制用同一份内容 */
const textPaste = computed(() => toPasteText(finalNodeData.value, textHeader.value))
const textStatsInfo = computed(() => textStats(textPaste.value))
const hasTextHeaderOption = computed(() => normalizeResult(finalNodeData.value).columns.length > 0)

const execPanelHeight = ref(300)
const execPanelMin = 120
const execPanelMax = 600
const isDragging = ref(false)
let dragStartY = 0
let dragStartH = 0

function onExecDragStart(e: MouseEvent) {
  isDragging.value = true
  dragStartY = e.clientY
  dragStartH = execPanelHeight.value
  document.addEventListener('mousemove', onExecDragMove)
  document.addEventListener('mouseup', onExecDragEnd)
}
function onExecDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  const delta = dragStartY - e.clientY
  execPanelHeight.value = Math.min(execPanelMax, Math.max(execPanelMin, dragStartH + delta))
}
function onExecDragEnd() {
  isDragging.value = false
  document.removeEventListener('mousemove', onExecDragMove)
  document.removeEventListener('mouseup', onExecDragEnd)
}

/** 新一轮执行结果到来时展开面板；保留用户上次选择的视图（概览/日志/JSON/映射/文本）与表头开关 */
watch(() => props.execResult, () => {
  execPanelCollapsed.value = false
})

/** 隐藏 _testFileBytes 的 execResult（base64 数据无展示意义且体积巨大） */
const sanitizedExecResult = computed(() => {
  const r = props.execResult
  if (!r) return null
  const copy = JSON.parse(JSON.stringify(r))
  if (copy.nodeDetailList) {
    for (const n of copy.nodeDetailList) {
      if (n.data && n.data._testFileBytes) delete n.data._testFileBytes
    }
  }
  return copy
})

function fmtData(data: any): string {
  if (data === null || data === undefined) return '-'
  if (typeof data === 'string') return data.length > 300 ? data.slice(0, 300) + '…' : data
  return JSON.stringify(data, null, 2)
}

function nodeTypeText(r: any): string {
  return NODE_TYPE_LABELS[r.nodeType] || r.nodeType
}

function statusText(r: any): string {
  if (r.status === 'STOPPED') return props.execResult?.stopType === 'NORMAL' ? '正常停止' : '流程停止'
  return r.success ? '成功' : '失败'
}

function durationText(r: any): string {
  return r.status === 'STOPPED' ? '-' : formatDuration(r.duration)
}

function resultText(r: any): string {
  if (r.status === 'STOPPED') return '-'
  return r.success ? fmtData(r.data) : (r.errorMessage || '-')
}

const execColumns = computed(() => {
  const list = props.execResult?.nodeDetailList
  if (!list || list.length === 0) return []
  return [
    { title: '#', key: '_idx', width: 36 },
    { title: '编码', key: 'nodeCode', width: 80 },
    { title: '名称', key: 'nodeName', width: 90 },
    { title: '类型', key: 'nodeType', width: 90, render: (r: any) => nodeTypeText(r) },
    { title: '状态', key: 'success', width: 72, render: (r: any) => r.status === 'STOPPED'
        ? h(NTag, { size: 'small', type: props.execResult?.stopType === 'NORMAL' ? 'info' : 'warning' }, { default: () => statusText(r) })
        : (r.success ? h(NTag, { size: 'small', type: 'success' }, { default: () => statusText(r) }) : h(NTag, { size: 'small', type: 'error' }, { default: () => statusText(r) })) },
    { title: '耗时', key: 'duration', width: 72, render: (r: any) => durationText(r) },
    { title: '结果', key: 'data', ellipsis: { tooltip: true }, render: (r: any) => resultText(r) },
  ]
})

const execData = computed(() => {
  const list = props.execResult?.nodeDetailList
  if (!list) return []
  return list.map((n: any, i: number) => ({ ...n, _idx: i + 1 }))
})

function openOutputFile() {
  if (props.execResult?.outputFile) {
    downloadFileById(props.execResult.outputFile.fileId)
  }
}

/** 概览视图的 TSV 列定义（与表格列一致） */
const overviewColumns = [
  { key: '_idx', label: '#' },
  { key: 'nodeCode', label: '编码' },
  { key: 'nodeName', label: '名称' },
  { key: 'nodeTypeText', label: '类型' },
  { key: 'statusText', label: '状态' },
  { key: 'durationText', label: '耗时' },
  { key: 'resultText', label: '结果' },
]

/** 概览视图可见内容（复制用，与表格渲染同一套取值逻辑） */
const overviewPaste = computed(() => rowsToPasteText(
  overviewColumns,
  execData.value.map((r: any) => ({
    _idx: r._idx,
    nodeCode: r.nodeCode,
    nodeName: r.nodeName,
    nodeTypeText: nodeTypeText(r),
    statusText: statusText(r),
    durationText: durationText(r),
    resultText: resultText(r),
  })),
  true
))

/** 当前视图可见内容的文本（复制 = 所见即所得） */
function currentCopyText(): string {
  switch (execViewMode.value) {
    case 'table': return overviewPaste.value
    case 'log': return logRef.value?.getCopyText?.() || ''
    case 'json': return props.execResult ? JSON.stringify(sanitizedExecResult.value, null, 2) : ''
    case 'mapped': return mappedRef.value?.getPasteText?.(true) || ''
    case 'text': return textPaste.value
    default: return ''
  }
}

async function copyCurrentView() {
  const text = currentCopyText()
  if (!text) { message.warning('没有可复制的内容'); return }
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function onExecViewChange(val: string) {
  execViewMode.value = val as any
}
</script>

<template>
  <div :style="{ height: execPanelHeight + 'px', flexShrink: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }">
    <div
      @mousedown="onExecDragStart"
      :style="{ height: '6px', background: isDragging ? '#4080ff' : '#e8e8e8', cursor: 'ns-resize', flexShrink: 0, transition: isDragging ? 'none' : 'background 0.2s' }"
    />
    <div style="flex:1; background:#fff; border-top:1px solid #e8e8e8; overflow:auto; padding:8px 16px; display:flex; flex-direction:column">
      <NSpace justify="space-between" align="center" style="margin-bottom: 6px">
        <NSpace align="center" :size="12">
          <span :style="{fontWeight:'600', fontSize:'13px', background: isHistory ? '#fffbe6' : 'transparent', padding: isHistory ? '2px 6px' : '0', borderRadius:'3px'}">测试结果</span>
          <template v-if="execResult">
            <span style="font-size:12px"><strong>流程：</strong>{{ execResult.flowName }}</span>
            <NTag :type="execResult.status === 'STOPPED' ? (execResult.stopType === 'NORMAL' ? 'info' : 'warning') : (execResult.success ? 'success' : 'error')" size="tiny">{{ execResult.status === 'STOPPED' ? (execResult.stopType === 'NORMAL' ? '正常停止' : '流程停止') : (execResult.success ? '成功' : '失败') }}</NTag>
            <span style="font-size:12px"><strong>总耗时：</strong>{{ formatDuration(execResult.duration) }}</span>
            <span v-if="execResult.startTime" style="font-size:12px"><strong>开始：</strong>{{ formatDateTime(execResult.startTime) }}</span>
          </template>
        </NSpace>
        <NSpace>
          <NButton v-if="execResult?.outputFile" size="tiny" type="info" @click="openOutputFile">📥 {{ execResult.outputFile.fileName }}{{ execResult.outputFile.sizeText ? ` (${execResult.outputFile.sizeText})` : '' }}</NButton>
          <NButton v-if="execResult && execResult.success" size="tiny" type="primary" @click="emit('save-examples')" :loading="savingExample">💾 保存结果示例</NButton>
          <NRadioGroup v-if="execResult" :value="execViewMode" size="small" @update:value="onExecViewChange">
            <NRadioButton value="table">概览</NRadioButton>
            <NRadioButton value="log">日志</NRadioButton>
            <NRadioButton value="json">JSON</NRadioButton>
            <NRadioButton value="mapped">映射</NRadioButton>
            <NRadioButton value="text">文本</NRadioButton>
          </NRadioGroup>
          <NCheckbox v-if="execViewMode === 'text' && hasTextHeaderOption" v-model:checked="textHeader" size="small">含表头</NCheckbox>
          <NButton size="tiny" :disabled="!execResult" @click="copyCurrentView">📋 复制</NButton>
          <span v-if="execViewMode === 'text'" style="font-size:11px;color:#999">{{ textStatsInfo.lines }} 行 · {{ textStatsInfo.chars }} 字符</span>
          <NButton size="tiny" @click="execPanelCollapsed = !execPanelCollapsed">{{ execPanelCollapsed ? '展开' : '收起' }}</NButton>
        </NSpace>
      </NSpace>

      <div v-if="execPanelCollapsed" style="text-align:center; padding:8px; color:#999; flex-shrink:0; font-size:12px">执行结果已收起，点击"展开"查看</div>
      <div v-else-if="executing" style="text-align:center; padding: 30px; color: #999; flex:1">执行中…</div>
      <template v-else-if="execResult && !execPanelCollapsed">
        <div v-if="execResult.status === 'STOPPED' && execResult.stopMessage"
          :style="{
            padding: '6px 10px',
            background: execResult.stopType === 'NORMAL' ? '#e8f4fd' : '#fffbe6',
            border: execResult.stopType === 'NORMAL' ? '1px solid #91caff' : '1px solid #ffe58f',
            borderRadius: '4px',
            marginBottom: '6px',
            fontSize: '12px',
            color: execResult.stopType === 'NORMAL' ? '#1677ff' : '#d48806',
            flexShrink: '0'
          }">
          {{ execResult.stopType === 'NORMAL' ? '✅' : '⏸' }} {{ execResult.stopType === 'NORMAL' ? '正常停止原因' : '停止原因' }}：{{ execResult.stopMessage }}
        </div>
        <div v-if="execResult.errorMessage" style="padding:6px 10px; background:#fff2f0; border:1px solid #ffccc7; border-radius:4px; margin-bottom:6px; font-size:12px; color:#cf1322; flex-shrink:0">{{ execResult.errorMessage }}</div>
        <NDataTable
          v-if="execViewMode === 'table'"
          :columns="execColumns"
          :data="execData"
          :max-height="execPanelHeight - 80"
          :single-line="false"
          size="small"
          bordered
          :row-props="(row: any) => ({ style: 'cursor: pointer', onClick: () => emit('select-node', row.nodeCode) })"
        />
        <!-- 日志视图：脚本节点打出的日志，与执行历史详情复用同一组件 -->
        <LogConsole v-if="execViewMode === 'log'" ref="logRef" :logs="execResult.scriptLogs || []" :max-height="execPanelHeight - 110" />
        <!-- 映射视图 -->
        <MappedResultTable v-if="execViewMode === 'mapped'" ref="mappedRef" :flow-id="flowId" :data="finalNodeData" />
        <pre v-if="execViewMode === 'json'" style="margin:0; flex:1; overflow:auto; background:#f5f5f5; padding:10px; border-radius:4px; font-size:11px">{{ JSON.stringify(sanitizedExecResult, null, 2) }}</pre>
        <!-- 文本视图：显示与复制同一份粘贴文本（TSV / 单列） -->
        <div v-if="execViewMode === 'text'" style="display:flex; flex-direction:column; flex:1; min-height:0">
          <textarea
            v-if="textPaste"
            readonly
            :value="textPaste"
            wrap="off"
            spellcheck="false"
            style="width:100%; flex:1; min-height:80px; resize:none; border:1px solid #e8e8e8; border-radius:4px; padding:8px; font-size:12px; font-family:monospace; line-height:1.5; background:#fafafa; box-sizing:border-box; white-space:pre; overflow:auto"
          />
          <div v-else style="text-align:center; padding:24px; color:#999; font-size:12px">暂无数据</div>
        </div>
      </template>
      <div v-else style="text-align:center; padding:30px; color:#999; flex:1">暂无结果</div>
    </div>
  </div>
</template>
