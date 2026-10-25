<script setup lang="ts">
/**
 * 广场内应用列表（应用广场详情视图）
 * 由 AppMarket.vue 在 currentMarket 非空时挂载；路由仍为 /app/market，marketId 放在 query 中。
 * 状态与逻辑随视图一起从 AppMarket.vue 拆出，避免广场列表和广场详情职责混在一个组件里。
 */
import { ref, onMounted, h, computed } from 'vue'
import { NDataTable, NButton, NSpace, NForm, NFormItem, NInput, NUpload, useMessage, NPopconfirm, NRadioGroup, NRadioButton } from 'naive-ui'
import { getMarketApps } from '../../api/appMarket'
import { executeApp, executeAppWithFile, updateApp, unshareApp } from '../../api/application'
import { cancelFlowExecution } from '../../api/flow'
import { downloadFileById } from '../../api/file'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import AppExecResultPanel from '../../components/AppExecResultPanel.vue'
import ParamFormModal from './ParamFormModal.vue'

const props = defineProps<{
  /** 当前广场（由路由 query 恢复或列表进入） */
  market: any
}>()

const emit = defineEmits<{ (e: 'back'): void }>()

const auth = useAuthStore()
const message = useMessage()

// 广场应用列表
const marketApps = ref<any[]>([])
const appsLoading = ref(false)
const appPage = ref(1)
const appPageSize = ref(10)
const appSearchName = ref('')
const appSearchUser = ref('')
const selectedEnvIds = ref<Record<number, number>>({})
const groupedMarketApps = computed(() => {
  const raw = marketApps.value; const map = new Map<number, any>()
  for (const r of raw) {
    const key = r.appId
    if (!map.has(key)) map.set(key, { ...r, envs: [] as any[], _selectedEnvId: r.envId })
    map.get(key)!.envs.push({ label: r.envName || `环境${r.envId}`, value: r.envId })
  }
  return Array.from(map.values())
})

// 执行状态（per-row）
const execResults = ref<Record<number, any>>({})
const execErrors = ref<Record<number, string>>({})
const executing = ref<Record<number, boolean>>({})
/** 各应用执行中请求的取消控制器（点“取消”时中断 HTTP 等待，结果不再渲染） */
const execControllers = ref<Record<number, AbortController>>({})
const resultViewMode = ref<Record<number, 'table' | 'json' | 'text'>>({})
/** 文本视图是否输出表头（仅对象表格有效），key=appId */
const resultHeader = ref<Record<number, boolean>>({})
const resultExpanded = ref<Record<number, boolean>>({})
const inputParamsLocal = ref<Record<number, string>>({})
const uploadFiles = ref<Record<number, File | null>>({})
const uploadKeys = ref<Record<number, number>>({})

// 参数表单填写（配置了 paramSchema 的应用才出现按钮）
const showParamFormModal = ref(false)
const paramFormSchema = ref<any>(null)
const paramFormRowId = ref<number | null>(null)
function openParamForm(row: any) {
  paramFormRowId.value = row.id
  paramFormSchema.value = row.paramSchema
  showParamFormModal.value = true
}
function onParamFormConfirm(json: string) {
  if (paramFormRowId.value !== null) {
    inputParamsLocal.value = { ...inputParamsLocal.value, [paramFormRowId.value]: json }
  }
}

/** 通过稳定ID下载文件（服务端 Content-Disposition 提供正确文件名） */
function downloadOutputFile(fileId: number, _fileName?: string) {
  downloadFileById(fileId)
}
function onFileChange(appId: number, data: any) {
  uploadFiles.value = { ...uploadFiles.value, [appId]: data?.file?.file || null }
}

function renderExpandContent(row: any) {
  const id = row.appId || row.id
  return h(AppExecResultPanel, {
    result: execResults.value[id],
    executing: !!executing.value[id],
    error: execErrors.value[id] || '',
    flowId: row.flowId,
    viewMode: resultViewMode.value[id] || 'table',
    header: resultHeader.value[id] !== false,
    'onUpdate:viewMode': (v: 'table' | 'json' | 'text') => {
      resultViewMode.value = { ...resultViewMode.value, [id]: v }
    },
    'onUpdate:header': (v: boolean) => {
      resultHeader.value = { ...resultHeader.value, [id]: v }
    },
    onCancel: () => cancelExecute(id, row.flowId)
  })
}

const appColumns: any[] = [
  { type: 'expand', width: 40, renderExpand: (row: any) => renderExpandContent(row) },
  { title: '应用名称', key: 'appName', width: 140 },
  { title: '所属流程', key: 'flowName', width: 100 },
  { title: '环境', key: 'envName', width: 200, render: (row: any) => {
    const envs = row.envs || []
    if (envs.length <= 1) return envs[0]?.label || row.envName || '-'
    const selEnvId = selectedEnvIds.value[row.appId] ?? envs[0]?.value
    return h(NRadioGroup, { value: selEnvId, size: 'small', onUpdateValue: (v: number) => { selectedEnvIds.value = { ...selectedEnvIds.value, [row.appId]: v } } },
      { default: () => envs.map((e: any) => h(NRadioButton, { value: e.value, key: e.value }, { default: () => e.label })) })
  }},
  { title: '输入参数', key: 'inputParams', width: 220, render: (row: any) => {
    const val = inputParamsLocal.value[row.id] ?? row.inputParams ?? ''
    const parts: any[] = [
      h('textarea', {
        value: val,
        onInput: (e: Event) => inputParamsLocal.value = { ...inputParamsLocal.value, [row.id]: (e.target as HTMLTextAreaElement).value },
        placeholder: 'JSON格式，如：{"key":"value"}',
        style: 'width:100%; min-height:36px; resize:vertical; overflow:auto; border:1px solid #d9d9d9; border-radius:4px; padding:4px 8px; font-size:12px; font-family:monospace; box-sizing:border-box',
        rows: 2
      }),
    ]
    if (row.paramSchema) {
      parts.push(h(NButton, { size: 'tiny', style: 'margin-top:4px', onClick: () => openParamForm(row) }, { default: () => '📋 表单填写' }))
    }
    return h('div', null, parts)
  }},
  { title: '共享者', key: 'sharedByName', width: 100 },
  { title: '共享时间', key: 'createTime', width: 160, render: (row: any) => formatDateTime(row.createTime) },
  { title: '操作', key: 'actions', width: 420, render: (row: any) => {
    const appId = row.appId; const hasFile = !!uploadFiles.value[appId]
    const btns: any[] = []
    if (row.exampleFileId) {
      btns.push(h(NButton, { size: 'small', type: 'info', onClick: () => downloadOutputFile(row.exampleFileId, row.exampleFileName || '示例文件') }, { default: () => '下载示例' }))
    }
    if (row.fileUploadEnabled) {
      btns.push(h(NUpload, { key: `up-${appId}-${uploadKeys.value[appId]||0}`, max: 1, defaultUpload: false, showFileList: false, onChange: (d: any) => onFileChange(appId, d) }, {
        default: () => h(NSpace, { size: 2 }, { default: () => {
          const parts = [h(NButton, { size: 'small', type: hasFile ? 'success' : 'default', style: hasFile ? '' : 'color:#bbb', title: hasFile ? uploadFiles.value[appId]!.name : '上传文件' }, { default: () => '⬆' })]
          if (hasFile) parts.push(h(NButton, { size: 'tiny', quaternary: true, onClick: (e: Event) => { e.stopPropagation(); uploadFiles.value = { ...uploadFiles.value, [appId]: null }; uploadKeys.value = { ...uploadKeys.value, [appId]: (uploadKeys.value[appId]||0)+1 } } }, { default: () => '✕' }))
          return parts
        }})
      }))
    }
    btns.push(h(NButton, { size: 'small', type: 'primary', disabled: !!executing.value[row.appId], onClick: () => handleExecute(row) }, { default: () => '执行' }))
    btns.push(h(NButton, { size: 'small', onClick: () => handleSaveParams(row) }, { default: () => '保存参数' }))
    // 移除共享：仅共享者本人或管理员可见（后端 unshareApp 同样校验，前端隐藏仅为体验）
    if (auth.isAdmin || row.sharedBy === auth.userId) {
      btns.push(h(NPopconfirm, { onPositiveClick: () => handleUnshare(row) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '移除' }),
        default: () => '确认从本广场移除该应用？'
      }))
    }
    return h(NSpace, null, { default: () => btns })
  }}
]

async function loadMarketApps() {
  appsLoading.value = true
  appPage.value = 1
  try {
    const res: any = await getMarketApps(props.market.id, { name: appSearchName.value || undefined, sharedBy: appSearchUser.value || undefined })
    marketApps.value = res || []
    const local: Record<number, string> = {}
    for (const r of res || []) { local[r.id] = inputParamsLocal.value[r.id] ?? r.inputParams ?? '' }
    inputParamsLocal.value = local
  } finally {
    appsLoading.value = false
  }
}

async function handleSaveParams(row: any) {
  const val = inputParamsLocal.value[row.id] ?? ''
  try {
    await updateApp(row.appId, { name: row.appName, flowId: row.flowId, envId: row.envId, inputParams: val, remark: '' })
    row.inputParams = val; message.success('参数已保存')
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '保存失败') }
}

/** 从当前广场移除该应用的共享（仅共享者本人/管理员，后端同步校验） */
async function handleUnshare(row: any) {
  try {
    await unshareApp(row.appId, props.market.id)
    message.success('已移除')
    loadMarketApps()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '移除失败') }
}

async function handleExecute(row: any) {
  const appId = row.appId
  const localParams = inputParamsLocal.value[row.id] ?? row.inputParams ?? ''
  if (localParams !== (row.inputParams ?? '')) {
    try {
      await updateApp(appId, { name: row.appName, flowId: row.flowId, envId: row.envId, inputParams: localParams, remark: '' })
      row.inputParams = localParams
    } catch (e: any) { message.error('保存参数失败'); return }
  }
  const selEnvId = selectedEnvIds.value[appId] ?? row.envs?.[0]?.value ?? row.envId
  executing.value = { ...executing.value, [appId]: true }
  execResults.value = { ...execResults.value, [appId]: undefined }
  execErrors.value = { ...execErrors.value, [appId]: '' }
  const controller = new AbortController()
  execControllers.value = { ...execControllers.value, [appId]: controller }
  // 保留用户上次选择的结果视图（表格/JSON/文本）与表头开关，重复执行不跳回表格
  resultExpanded.value = { ...resultExpanded.value, [appId]: true }
  try {
    const file = uploadFiles.value[appId] || null
    let res: any
    if (file) {
      const form = new FormData(); form.append('file', file)
      res = await executeAppWithFile(appId, form, props.market.id, selEnvId, controller.signal)
    } else {
      res = await executeApp(appId, props.market.id, selEnvId, controller.signal)
    }
    execResults.value = { ...execResults.value, [appId]: res }
  } catch (e: any) {
    // 用户主动取消：不展示任何结果（后端已由 cancelExecute 通知止损）
    if (e?.code !== 'ERR_CANCELED') {
      execErrors.value = { ...execErrors.value, [appId]: e?.response?.data?.message || e?.message || '执行失败' }
    }
  } finally {
    executing.value = { ...executing.value, [appId]: false }
    const cs = { ...execControllers.value }; delete cs[appId]; execControllers.value = cs
  }
}

/** 取消正在执行的应用：中断 HTTP 等待 + 通知后端在下一节点前终止（取消接口按 flowId） */
function cancelExecute(appId: number, flowId: number | null) {
  execControllers.value[appId]?.abort()
  if (flowId != null) cancelFlowExecution(flowId).catch(() => { /* 忽略 */ })
  message.info('已取消执行')
}

onMounted(() => { loadMarketApps() })

const expandedRowKeys = computed(() => groupedMarketApps.value.filter((r: any) => resultExpanded.value[r.appId]).map((r: any) => r.appId))
function onExpandedKeysChange(keys: Array<string | number>) {
  const r: Record<number, boolean> = {}
  groupedMarketApps.value.forEach((d: any) => r[d.appId] = keys.includes(d.appId))
  resultExpanded.value = r
}
</script>

<template>
  <div>
    <NSpace style="margin-bottom:12px">
      <NButton @click="emit('back')">← 返回广场列表</NButton>
      <h3 style="margin:0">{{ market.name }}</h3>
    </NSpace>

    <NForm :model="{ appSearchName, appSearchUser }" label-placement="left" style="margin-bottom:12px">
      <NSpace>
        <NFormItem label="应用名"><NInput v-model:value="appSearchName" placeholder="请输入" clearable @keyup.enter="loadMarketApps" /></NFormItem>
        <NFormItem label="共享者"><NInput v-model:value="appSearchUser" placeholder="请输入" clearable @keyup.enter="loadMarketApps" /></NFormItem>
        <NButton type="primary" @click="loadMarketApps">搜索</NButton>
        <NButton @click="appSearchName='';appSearchUser='';loadMarketApps()">重置</NButton>
      </NSpace>
    </NForm>

    <NDataTable :columns="appColumns" :data="groupedMarketApps" :loading="appsLoading" :row-key="(row: any) => row.appId"
      :single-line="false"
      :expanded-row-keys="expandedRowKeys"
      @update:expanded-row-keys="onExpandedKeysChange"
      :pagination="{ page: appPage, pageSize: appPageSize, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { appPage = p }, onUpdatePageSize: (s: number) => { appPageSize = s; appPage = 1 } }"
      table-layout="fixed"
      bordered />

    <!-- 参数表单填写 Modal -->
    <ParamFormModal v-model:show="showParamFormModal" :schema="paramFormSchema" @confirm="onParamFormConfirm" />
  </div>
</template>
