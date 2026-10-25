<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NUpload, useMessage, NPopconfirm, NRadioGroup, NRadioButton } from 'naive-ui'
import { getAppPage, createApp, updateApp, deleteApp, executeApp, executeAppWithFile, shareApp, unshareApp, getAppSharedMarkets } from '../../api/application'
import { getFlowPage, cancelFlowExecution } from '../../api/flow'
import { getEnvActive } from '../../api/environment'
import { getMyMarkets } from '../../api/appMarket'
import { downloadFileById } from '../../api/file'
import { exportApps } from '../../api/importExport'
import { downloadTextFile, buildPackageName } from '../../utils/download'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import AppExecResultPanel from '../../components/AppExecResultPanel.vue'
import ParamFormModal from './ParamFormModal.vue'
import ImportPreviewModal from '../../components/ImportPreviewModal.vue'

const message = useMessage()
const router = useRouter()
const auth = useAuthStore()

// 导入导出
const showImportModal = ref(false)
const exportingId = ref<number | null>(null)
async function handleExport(row: any) {
  exportingId.value = row.id
  try {
    const json: any = await exportApps([row.id])
    downloadTextFile(json, buildPackageName(`app-${row.name}`))
    message.success('导出成功')
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '导出失败')
  } finally { exportingId.value = null }
}

/** 通过稳定ID下载文件（服务端 Content-Disposition 提供正确文件名，无需前端指定） */
function downloadOutputFile(fileId: number, _fileName?: string) {
  downloadFileById(fileId)
}
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchName = ref('')
const searchFlowName = ref('')
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', flowId: null as number | null, envId: null as number | null, inputParams: '', remark: '' })
const flowOptions = ref<{ label: string; value: number }[]>([])
const envOptions = ref<{ label: string; value: number }[]>([])
const execResults = ref<Record<number, any>>({})
const execErrors = ref<Record<number, string>>({})
const executing = ref<Record<number, boolean>>({})
/** 各应用执行中请求的取消控制器（点“取消”时中断 HTTP 等待，结果不再渲染） */
const execControllers = ref<Record<number, AbortController>>({})
const resultViewMode = ref<Record<number, 'table' | 'json' | 'text'>>({})
/** 文本视图是否输出表头（仅对象表格有效），key=app.id */
const resultHeader = ref<Record<number, boolean>>({})
const resultExpanded = ref<Record<number, boolean>>({})
// 本地输入参数缓存（编辑中的值），key=app.id
const inputParamsLocal = ref<Record<number, string>>({})
// 上传文件缓存，key=app.id
const uploadFiles = ref<Record<number, File | null>>({})
// NUpload 强制刷新 key（清除后 +1 强制重建组件）
const uploadKeys = ref<Record<number, number>>({})

// 参数表单填写（配置了 paramSchema 的应用才出现按钮）
const showParamFormModal = ref(false)
const paramFormSchema = ref<any>(null)
const paramFormAppId = ref<number | null>(null)
function openParamForm(row: any) {
  paramFormAppId.value = row.id
  paramFormSchema.value = row.paramSchema
  showParamFormModal.value = true
}
function onParamFormConfirm(json: string) {
  if (paramFormAppId.value !== null) {
    inputParamsLocal.value = { ...inputParamsLocal.value, [paramFormAppId.value]: json }
  }
}

// NaiveUI 2.44.1 使用 type:'expand' 列 + renderExpand，不支持 #expand slot
function renderExpandContent(row: any) {
  const id = row.id
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

const columns: any[] = [
  { type: 'expand', width: 40, renderExpand: (row: any) => renderExpandContent(row) },
  { title: '序号', key: '_idx', width: 60 },
  { title: '应用名称', key: 'name', width: 120 },
  { title: '工作流', key: 'flowName', width: 160, render: (row: any) => h(NSpace, { size: 6, align: 'center' }, {
    default: () => [
      row.flowId ? h(NButton, { size: 'tiny', type: 'primary', onClick: () => router.push(`/flow/designer/${row.flowId}`) }, { default: () => '设计' }) : null,
      h('span', null, row.flowName || '-')
    ]
  })},
  { title: '环境', key: 'envId', width: 180, render: (row: any) => {
    if (!envOptions.value.length) return ''
    return h(NRadioGroup, {
      value: row.envId || null,
      size: 'small',
      onUpdateValue: (val: number) => handleEnvChange(row, val)
    }, {
      default: () => envOptions.value.map(e => h(NRadioButton, { value: e.value, key: e.value }, { default: () => e.label }))
    })
  }},
  { title: '输入参数', key: 'inputParams', width: 250, render: (row: any) => {
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
  { title: '备注', key: 'remark', width: 100, ellipsis: { tooltip: true } },
  { title: '修改时间', key: 'updateTime', width: 170, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 480, render: (row: any) => h(NSpace, null, {
    default: () => [
      row.exampleFileId
        ? h(NButton, { size: 'small', type: 'info', onClick: () => downloadOutputFile(row.exampleFileId, row.exampleFileName) }, { default: () => '下载示例' })
        : null,
      row.fileUploadEnabled
        ? h(NUpload, { key: `up-${row.id}-${uploadKeys.value[row.id]||0}`, max: 1, defaultUpload: false, showFileList: false, onChange: (data: any) => onFileChange(row.id, data) }, {
            default: () => {
              const hasFile = !!uploadFiles.value[row.id]
              return h(NSpace, { size: 2 }, { default: () => {
                const parts = [
                  h(NButton, {
                    size: 'small', type: hasFile ? 'success' : 'default',
                    style: hasFile ? '' : 'color:#bbb',
                    title: hasFile ? uploadFiles.value[row.id]!.name : '上传文件'
                  }, { default: () => '⬆' }),
                ]
                if (hasFile) {
                  parts.push(h(NButton, { size: 'tiny', quaternary: true, onClick: (e: Event) => { e.stopPropagation(); uploadFiles.value = { ...uploadFiles.value, [row.id]: null }; uploadKeys.value = { ...uploadKeys.value, [row.id]: (uploadKeys.value[row.id] || 0) + 1 } } }, { default: () => '✕' }))
                }
                return parts
              }})
            }
          })
        : null,
      h(NButton, { size: 'small', type: 'primary', disabled: !!executing.value[row.id], onClick: () => handleExecute(row) }, { default: () => '执行' }),
      h(NButton, { size: 'small', onClick: () => handleSaveParams(row) }, { default: () => '保存参数' }),
      h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
      h(NButton, { size: 'small', type: 'info', onClick: () => openShare(row) }, { default: () => '共享' }),
      h(NButton, { size: 'small', loading: exportingId.value === row.id, onClick: () => handleExport(row) }, { default: () => '导出' }),
      h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除？'
      })
    ]
  })},
]

// 共享
const showShareModal = ref(false)
const shareAppId = ref<number | null>(null)
const shareMarkets = ref<any[]>([])
const shareTargetId = ref<number | null>(null)
const shareEnvIds = ref<number[]>([])
// 该应用当前已共享到的广场（按 marketId 分组，供弹窗内展示与移除）
const currentShares = ref<{ marketId: number; marketName: string; sharedBy: number; envNames: string }[]>([])

async function loadCurrentShares(appId: number) {
  try {
    const rows: any = await getAppSharedMarkets(appId)
    const map = new Map<number, any>()
    for (const r of rows || []) {
      if (!map.has(r.marketId)) map.set(r.marketId, { marketId: r.marketId, marketName: r.marketName, sharedBy: r.sharedBy, envIds: [] as number[] })
      if (r.envId != null) map.get(r.marketId).envIds.push(r.envId)
    }
    currentShares.value = Array.from(map.values()).map((g: any) => ({
      marketId: g.marketId, marketName: g.marketName, sharedBy: g.sharedBy,
      envNames: g.envIds.map((id: number) => envOptions.value.find(e => e.value === id)?.label || `环境${id}`).join('、')
    }))
  } catch { currentShares.value = [] }
}

async function openShare(row: any) {
  shareAppId.value = row.id; shareTargetId.value = null; shareEnvIds.value = []
  currentShares.value = []
  try {
    shareMarkets.value = await getMyMarkets() as any
  } catch { shareMarkets.value = [] }
  showShareModal.value = true
  loadCurrentShares(row.id)
}
async function handleShare() {
  if (!shareTargetId.value) { message.error('请选择目标广场'); return }
  if (!shareEnvIds.value.length) { message.error('请选择环境'); return }
  try {
    await shareApp(shareAppId.value!, shareTargetId.value, shareEnvIds.value)
    message.success('共享成功')
    // 保持弹窗打开：刷新已共享列表并清空选择，便于继续共享到其它广场或移除
    shareTargetId.value = null; shareEnvIds.value = []
    if (shareAppId.value != null) loadCurrentShares(shareAppId.value)
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '共享失败') }
}
/** 从指定广场移除该应用的共享（仅共享者本人/管理员，后端同步校验） */
async function handleUnshareFromShare(marketId: number) {
  if (shareAppId.value == null) return
  try {
    await unshareApp(shareAppId.value, marketId)
    message.success('已移除')
    loadCurrentShares(shareAppId.value)
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '移除失败') }
}

async function load() {
  loading.value = true
  try {
    const res: any = await getAppPage({ pageNum: page.value, pageSize: pageSize.value, name: searchName.value || undefined, flowName: searchFlowName.value || undefined })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
    // 初始化本地参数缓存（保留已有的编辑值）
    const local: Record<number, string> = {}
    for (const r of res.list || []) {
      local[r.id] = inputParamsLocal.value[r.id] ?? r.inputParams ?? ''
    }
    inputParamsLocal.value = local
  } finally { loading.value = false }
}

async function loadEnvs() {
  try { const res: any = await getEnvActive(); envOptions.value = (res || []).map((e: any) => ({ label: e.name, value: e.id })) } catch { /* ignore */ }
}

async function loadFlows() {
  try { const res: any = await getFlowPage({ pageNum: 1, pageSize: 200 }); flowOptions.value = (res.list || []).map((f: any) => ({ label: f.name, value: f.id })) } catch { /* ignore */ }
}

async function handleEnvChange(row: any, envId: number) {
  try {
    await updateApp(row.id, {
      name: row.name, flowId: row.flowId, envId: envId,
      inputParams: row.inputParams || '', remark: row.remark || ''
    })
    row.envId = envId
    message.success(`环境已切换`)
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '切换失败') }
}

async function handleSaveParams(row: any) {
  const val = inputParamsLocal.value[row.id] ?? ''
  try {
    await updateApp(row.id, {
      name: row.name, flowId: row.flowId, envId: row.envId,
      inputParams: val, remark: row.remark || ''
    })
    row.inputParams = val
    message.success('参数已保存')
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '保存失败') }
}

function openCreate() {
  isEdit.value = false; editId.value = null
  form.value = { name: '', flowId: null, envId: null, inputParams: '', remark: '' }
  loadFlows(); loadEnvs(); showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { name: row.name, flowId: row.flowId, envId: row.envId, inputParams: row.inputParams || '', remark: row.remark || '' }
  loadFlows(); loadEnvs(); showModal.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) await updateApp(editId.value!, form.value)
    else await createApp(form.value)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDelete(id: number) { await deleteApp(id); message.success('删除成功'); load() }

function onFileChange(appId: number, data: any) {
  uploadFiles.value = { ...uploadFiles.value, [appId]: data?.file?.file || null }
}

async function handleExecute(row: any) {
  // 先把当前编辑的入参保存到数据库，再执行
  const localParams = inputParamsLocal.value[row.id] ?? row.inputParams ?? ''
  if (localParams !== (row.inputParams ?? '')) {
    try {
      await updateApp(row.id, { name: row.name, flowId: row.flowId, envId: row.envId, inputParams: localParams, remark: row.remark || '' })
      row.inputParams = localParams
    } catch (e: any) { message.error('保存参数失败'); return }
  }
  executing.value = { ...executing.value, [row.id]: true }
  execResults.value = { ...execResults.value, [row.id]: undefined }
  execErrors.value = { ...execErrors.value, [row.id]: '' }
  const controller = new AbortController()
  execControllers.value = { ...execControllers.value, [row.id]: controller }
  // 保留用户上次选择的结果视图（表格/JSON/文本）与表头开关，重复执行不跳回表格
  resultExpanded.value = { ...resultExpanded.value, [row.id]: true }
  try {
    const file = uploadFiles.value[row.id] || null
    let res: any
    if (file) {
      const form = new FormData()
      form.append('file', file)
      res = await executeAppWithFile(row.id, form, undefined, undefined, controller.signal)
    } else {
      res = await executeApp(row.id, undefined, undefined, controller.signal)
    }
    execResults.value = { ...execResults.value, [row.id]: res }
  } catch (e: any) {
    // 用户主动取消：不展示任何结果（后端已由 cancelExecute 通知止损）
    if (e?.code !== 'ERR_CANCELED') {
      execErrors.value = { ...execErrors.value, [row.id]: e?.response?.data?.message || e?.message || '执行失败' }
    }
  } finally {
    executing.value = { ...executing.value, [row.id]: false }
    const cs = { ...execControllers.value }; delete cs[row.id]; execControllers.value = cs
  }
}

/** 取消正在执行的应用：中断 HTTP 等待 + 通知后端在下一节点前终止（取消接口按 flowId） */
function cancelExecute(appId: number, flowId: number | null) {
  execControllers.value[appId]?.abort()
  if (flowId != null) cancelFlowExecution(flowId).catch(() => { /* 忽略 */ })
  message.info('已取消执行')
}

onMounted(async () => { await loadEnvs(); load() })

const expandedRowKeys = computed(() => data.value.filter((r: any) => resultExpanded.value[r.id]).map((r: any) => r.id))
function onExpandedKeysChange(keys: Array<string | number>) {
  const r: Record<number, boolean> = {}
  data.value.forEach((d: any) => r[d.id] = keys.includes(d.id))
  resultExpanded.value = r
}
</script>

<template>
  <div>
    <NForm label-placement="left" style="margin-bottom:12px">
      <NSpace>
        <NFormItem label="应用名称"><NInput v-model:value="searchName" placeholder="请输入" clearable @keyup.enter="page=1;load()" /></NFormItem>
        <NFormItem label="工作流"><NInput v-model:value="searchFlowName" placeholder="请输入" clearable @keyup.enter="page=1;load()" /></NFormItem>
        <NButton type="primary" @click="page=1;load()">搜索</NButton>
        <NButton @click="searchName='';searchFlowName='';page=1;load()">重置</NButton>
        <NButton type="primary" @click="openCreate">新增应用</NButton>
        <NButton @click="showImportModal = true">导入应用</NButton>
      </NSpace>
    </NForm>
    <NDataTable :columns="columns" :data="data" :loading="loading" :row-key="(row: any) => row.id"
      :single-line="false"
      :expanded-row-keys="expandedRowKeys"
      @update:expanded-row-keys="onExpandedKeysChange"
      :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote table-layout="fixed" bordered />

    <!-- 创建/编辑 Modal -->
    <NModal v-model:show="showModal" :key="String(isEdit) + String(editId)" preset="card" :title="isEdit ? '编辑应用' : '新增应用'" style="width: 520px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="90" require-mark-placement="right">
        <NFormItem label="应用名称" required><NInput v-model:value="form.name" placeholder="请输入应用名称" /></NFormItem>
        <NFormItem label="选择工作流" required><NSelect v-model:value="form.flowId" :options="flowOptions" placeholder="请选择工作流" filterable /></NFormItem>
        <NFormItem label="执行环境"><NSelect v-model:value="form.envId" :options="envOptions" placeholder="默认环境" clearable /></NFormItem>
        <NFormItem label="输入参数"><NInput v-model:value="form.inputParams" type="textarea" :rows="4" placeholder='JSON格式，会传给工作流的 flow_param' /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" /></NFormItem>
        <NSpace justify="end"><NButton @click="showModal = false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
      </NForm>
    </NModal>

    <!-- 共享 Modal -->
    <NModal v-model:show="showShareModal" preset="card" title="共享到应用广场" style="width:460px" :mask-closable="false">
      <NForm label-placement="left" label-width="80">
        <NFormItem label="选择广场" required><NSelect v-model:value="shareTargetId" :options="shareMarkets.map((m:any)=>({label:m.name,value:m.id}))" placeholder="请选择目标广场" /></NFormItem>
        <NFormItem label="指定环境" required><NSelect v-model:value="shareEnvIds" :options="envOptions" multiple placeholder="请选择所有者的环境" /></NFormItem>
        <NFormItem label="已共享到">
          <div style="width:100%">
            <span v-if="!currentShares.length" style="color:#999">尚未共享到任何广场</span>
            <div v-for="s in currentShares" :key="s.marketId" style="display:flex;align-items:center;justify-content:space-between;gap:8px;padding:4px 0;border-bottom:1px solid #f0f0f0">
              <span style="overflow:hidden;text-overflow:ellipsis">{{ s.marketName }}<span style="color:#999;font-size:12px">（环境：{{ s.envNames || '-' }}）</span></span>
              <NPopconfirm v-if="auth.isAdmin || s.sharedBy === auth.userId" @positive-click="handleUnshareFromShare(s.marketId)">
                <template #trigger><NButton size="tiny" type="error" quaternary>移除</NButton></template>
                确认从「{{ s.marketName }}」移除该应用？
              </NPopconfirm>
              <span v-else style="color:#bbb;font-size:12px;white-space:nowrap">仅共享者可移除</span>
            </div>
          </div>
        </NFormItem>
        <NSpace justify="end"><NButton @click="showShareModal=false">关闭</NButton><NButton type="primary" @click="handleShare">共享</NButton></NSpace>
      </NForm>
    </NModal>
    <!-- 参数表单填写 Modal -->
    <ParamFormModal v-model:show="showParamFormModal" :schema="paramFormSchema" @confirm="onParamFormConfirm" />

    <!-- 导入弹窗 -->
    <ImportPreviewModal v-model:show="showImportModal" @done="load" />
  </div>
</template>
