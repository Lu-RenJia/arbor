<script setup lang="ts">
import { ref, computed, h } from 'vue'
import { NModal, NUpload, NButton, NSpace, NDataTable, NTag, NRadioGroup, NRadioButton, NAlert, NDivider } from 'naive-ui'
import { importPreview, importExecute } from '../api/importExport'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ (e: 'update:show', v: boolean): void; (e: 'done'): void }>()

/** 阶段：pick-选择文件 preview-预览确认 result-执行结果 */
const phase = ref<'pick' | 'preview' | 'result'>('pick')
const packageJson = ref('')
const fileName = ref('')
const preview = ref<any>(null)
const result = ref<any>(null)
const loading = ref(false)
const executing = ref(false)

/** 冲突默认策略（全部跳过 / 全部覆盖 开关） */
const defaultAction = ref<'SKIP' | 'OVERWRITE'>('SKIP')
/** 单项覆盖策略：uid → SKIP/OVERWRITE */
const overrides = ref<Record<string, string>>({})

const typeLabels: Record<string, string> = {
  FLOW: '流程',
  APP: '应用',
  DB_SCRIPT: '数据库脚本',
  PROGRAM_SCRIPT: '程序脚本',
  API_CONFIG: '接口配置',
  REDIS_SCRIPT: 'Redis脚本',
  GLOBAL_PARAM: '全局参数',
}

const items = computed(() => preview.value?.items || [])

const columns = [
  { title: '类型', key: 'type', width: 100, render: (r: any) => typeLabels[r.type] || r.type },
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  {
    title: '状态', key: 'conflict', width: 80,
    render: (r: any) => r.conflict
      ? h(NTag, { size: 'small', type: 'warning' }, { default: () => '冲突' })
      : h(NTag, { size: 'small', type: 'success' }, { default: () => '新增' })
  },
  { title: '目标现有名称', key: 'existingName', width: 160, render: (r: any) => r.existingName || '-' },
  { title: '归属流程', key: 'ownerFlowName', width: 140, render: (r: any) => r.ownerFlowName ? `${r.ownerFlowName}（内联）` : '-' },
  {
    title: '处理方式', key: '_action', width: 160,
    render: (r: any) => {
      if (!r.conflict) return h('span', { style: 'color:#999;font-size:12px' }, '新建')
      const val = overrides.value[r.uid] || defaultAction.value
      return h(NRadioGroup, {
        value: val, size: 'small',
        onUpdateValue: (v: string) => { overrides.value = { ...overrides.value, [r.uid]: v } }
      }, {
        default: () => [
          h(NRadioButton, { value: 'SKIP' }, { default: () => '跳过' }),
          h(NRadioButton, { value: 'OVERWRITE' }, { default: () => '覆盖' }),
        ]
      })
    }
  },
]

function closeModal() { emit('update:show', false) }

function reset() {
  phase.value = 'pick'
  packageJson.value = ''
  fileName.value = ''
  preview.value = null
  result.value = null
  defaultAction.value = 'SKIP'
  overrides.value = {}
}

function onVisibleChange(v: boolean) {
  if (!v) reset()
  emit('update:show', v)
}

/** 选中文件 → 读取 → 请求预览 */
async function onFileSelect(data: any) {
  const file: File | null = data?.file?.file
  if (!file) return
  loading.value = true
  try {
    const text = await file.text()
    const res: any = await importPreview(text)
    packageJson.value = text
    fileName.value = file.name
    preview.value = res
    defaultAction.value = 'SKIP'
    overrides.value = {}
    phase.value = 'preview'
  } catch (e: any) {
    // request.ts 已统一弹错误提示
  } finally { loading.value = false }
}

function onDefaultActionChange(v: 'SKIP' | 'OVERWRITE') {
  defaultAction.value = v
  overrides.value = {} // 全局开关优先，清空单项设置
}

async function handleExecute() {
  executing.value = true
  try {
    const res: any = await importExecute(packageJson.value, defaultAction.value, overrides.value)
    result.value = res
    phase.value = 'result'
    emit('done')
  } catch (e: any) {
    // request.ts 已统一弹错误提示
  } finally { executing.value = false }
}
</script>

<template>
  <NModal :show="props.show" preset="card" title="导入" style="width: 900px" :mask-closable="false" @update:show="onVisibleChange">
    <!-- 阶段一：选择文件 -->
    <div v-if="phase === 'pick'">
      <NUpload
        :default-upload="false"
        accept=".json"
        :max="1"
        directory-dnd
        @change="onFileSelect"
      >
        <div style="padding: 40px; text-align: center; border: 1px dashed #d9d9d9; border-radius: 8px; color: #666">
          <div v-if="loading">解析预览中…</div>
          <template v-else>
            <div style="font-size: 15px; margin-bottom: 8px">点击或拖拽导入包文件到此处</div>
            <div style="font-size: 12px; color: #999">仅支持 .json 格式的 arbor-package 导出文件</div>
          </template>
        </div>
      </NUpload>
    </div>

    <!-- 阶段二：预览确认 -->
    <template v-else-if="phase === 'preview' && preview">
      <div style="margin-bottom: 8px; font-size: 13px; color: #666">
        文件：<strong>{{ fileName }}</strong>
        <span style="margin-left: 16px">新增 <strong style="color:#18a058">{{ preview.newCount }}</strong> 项</span>
        <span style="margin-left: 12px">冲突 <strong style="color:#f0a020">{{ preview.conflictCount }}</strong> 项</span>
      </div>

      <NAlert v-if="preview.missingDatasources?.length" type="error" style="margin-bottom: 8px" :show-icon="true">
        缺少同名 MySQL 数据源：{{ preview.missingDatasources.join('、') }}（需先在数据源管理中创建，否则相关对象数据源绑定为空）
      </NAlert>
      <NAlert v-if="preview.missingRedisDatasources?.length" type="error" style="margin-bottom: 8px" :show-icon="true">
        缺少同名 Redis 数据源：{{ preview.missingRedisDatasources.join('、') }}
      </NAlert>
      <NAlert v-if="preview.missingParams?.length" type="warning" style="margin-bottom: 8px" :show-icon="true">
        缺少全局参数：{{ preview.missingParams.join('、') }}（导入后请补建并在各环境配置参数值）
      </NAlert>
      <NAlert v-for="(w, i) in (preview.warnings || [])" :key="i" type="warning" style="margin-bottom: 8px" :show-icon="true">
        {{ w }}
      </NAlert>

      <NSpace align="center" style="margin-bottom: 8px">
        <span style="font-size: 13px">冲突项统一处理：</span>
        <NRadioGroup :value="defaultAction" size="small" @update:value="onDefaultActionChange">
          <NRadioButton value="SKIP">全部跳过</NRadioButton>
          <NRadioButton value="OVERWRITE">全部覆盖</NRadioButton>
        </NRadioGroup>
        <span style="font-size: 12px; color: #999">（可在下方逐项调整）</span>
      </NSpace>

      <NDataTable :columns="columns" :data="items" :max-height="380" size="small" bordered />

      <NDivider style="margin: 12px 0" />
      <NSpace justify="end">
        <NButton @click="reset">重新选择</NButton>
        <NButton @click="closeModal">取消</NButton>
        <NButton type="primary" :loading="executing" @click="handleExecute">开始导入</NButton>
      </NSpace>
    </template>

    <!-- 阶段三：执行结果 -->
    <template v-else-if="phase === 'result' && result">
      <div style="display: flex; gap: 24px; margin-bottom: 12px; font-size: 14px">
        <span>新建 <strong style="color:#18a058">{{ result.created }}</strong></span>
        <span>覆盖 <strong style="color:#2080f0">{{ result.updated }}</strong></span>
        <span>跳过 <strong style="color:#f0a020">{{ result.skipped }}</strong></span>
      </div>
      <NAlert v-for="(w, i) in (result.warnings || [])" :key="'w' + i" type="warning" style="margin-bottom: 6px" :show-icon="true">
        {{ w }}
      </NAlert>
      <div style="max-height: 340px; overflow: auto; background: #fafafa; border-radius: 6px; padding: 10px 12px; font-size: 13px">
        <div v-for="(d, i) in (result.details || [])" :key="i" style="line-height: 1.9">{{ d }}</div>
        <div v-if="!result.details?.length" style="color: #999">无变更</div>
      </div>
      <NDivider style="margin: 12px 0" />
      <NSpace justify="end">
        <NButton type="primary" @click="closeModal">完成</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
