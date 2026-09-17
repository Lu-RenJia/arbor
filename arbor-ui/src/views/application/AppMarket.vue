<script setup lang="ts">
import { ref, onMounted, h, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NUpload, useMessage, NPopconfirm, NRadioGroup, NRadioButton, NTransfer } from 'naive-ui'
import { getMarketPage, createMarket, updateMarket, deleteMarket, getMarketApps, getMarketUsers, assignMarketUsers } from '../../api/appMarket'
import { getUserList } from '../../api/userManage'
import { executeApp, executeAppWithFile, updateApp, unshareApp } from '../../api/application'
import { cancelFlowExecution } from '../../api/flow'
import { getEnvActive } from '../../api/environment'
import { downloadFileById } from '../../api/file'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import MappedResultTable from '../../components/MappedResultTable.vue'
import ParamFormModal from './ParamFormModal.vue'

const auth = useAuthStore()
const message = useMessage()
const route = useRoute()
const router = useRouter()

// 广场列表
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchName = ref('')
const showSearch = ref(true)

// 广场 CRUD
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', remark: '' })

// 分配人员
const showUserModal = ref(false)
const userMarketId = ref<number | null>(null)
const userMarketName = ref('')
const allUsers = ref<any[]>([])
const selectedUserIds = ref<number[]>([])

// 广场应用列表
const currentMarket = ref<any>(null)
const marketApps = ref<any[]>([])
const appsLoading = ref(false)
const appPage = ref(1)
const appPageSize = ref(10)
const appSearchName = ref('')
const appSearchUser = ref('')
const selectedEnvIds = ref<Record<number, number>>({})
const envOptions = ref<any[]>([])
const groupedMarketApps = computed(() => {
  const raw = marketApps.value; const map = new Map<number, any>()
  for (const r of raw) {
    const key = r.appId
    if (!map.has(key)) map.set(key, { ...r, envs: [] as any[], _selectedEnvId: r.envId })
    map.get(key)!.envs.push({ label: r.envName || `环境${r.envId}`, value: r.envId })
  }
  return Array.from(map.values())
})

// 执行状态（per-row，对齐我的应用）
const execResults = ref<Record<number, any>>({})
const execErrors = ref<Record<number, string>>({})
const executing = ref<Record<number, boolean>>({})
/** 各应用执行中请求的取消控制器（点“取消”时中断 HTTP 等待，结果不再渲染） */
const execControllers = ref<Record<number, AbortController>>({})
const resultViewMode = ref<Record<number, 'table' | 'json'>>({})
const resultExpanded = ref<Record<number, boolean>>({})
const inputParamsLocal = ref<Record<number, string>>({})
const uploadFiles = ref<Record<number, File | null>>({})
const uploadKeys = ref<Record<number, number>>({})

// 参数表单填写（配置了 paramSchema 的应用才出现按钮；共享查看者可用表单但看不到配置入口）
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
  if (executing.value[id]) {
    return h('div', { style: 'padding:24px;text-align:center' }, [
      h('span', { style: 'display:inline-block;width:20px;height:20px;border:3px solid #e8e8e8;border-top-color:#18a058;border-radius:50%;animation:arbor-spin 0.8s linear infinite;vertical-align:middle;margin-right:8px' }),
      h('span', { style: 'color:#999;font-size:13px;vertical-align:middle;margin-right:12px' }, '执行中…'),
      h(NButton, { size: 'tiny', type: 'error', onClick: () => cancelExecute(id, row.flowId) }, { default: () => '取消执行' }),
    ])
  }
  const result = execResults.value[id]
  if (result !== undefined) {
    // 流程停止：显示停止提示信息
    if (result.stopped) {
      return h('div', { style: 'padding:12px 16px' }, [
        h(NSpace, { align: 'center', size: 12 }, {
          default: () => [
            h('span', { style: 'font-weight:600;color:#faad14' }, '⏹ 流程停止'),
            result.duration != null ? h('span', { style: 'font-size:11px;color:#999' }, `耗时 ${result.duration}ms`) : null,
            result.nodeCount != null ? h('span', { style: 'font-size:11px;color:#999' }, `${result.nodeCount} 节点`) : null,
          ]
        }),
        h('div', { style: 'margin-top:8px;padding:8px 12px;background:#fffbe6;border:1px solid #ffe58f;border-radius:4px;font-size:13px;color:#ad6800' },
          result.data || '流程已停止'),
      ])
    }
    const mode = resultViewMode.value[id] || 'table'
    return h('div', { style: 'padding:12px 16px' }, [
      h('div', { style: 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px' }, [
        h(NSpace, { align: 'center', size: 12 }, {
          default: () => [
            h('span', {
              style: { fontWeight: '600', color: result.success ? '#52c41a' : '#ff4d4f' }
            }, result.success ? '✓ 成功' : '✗ 失败'),
            result.duration != null ? h('span', { style: 'font-size:11px;color:#999' }, `耗时 ${result.duration}ms`) : null,
            result.nodeCount != null ? h('span', { style: 'font-size:11px;color:#999' }, `${result.nodeCount} 节点`) : null,
            result.outputFile ? h(NButton, { size: 'tiny', type: 'info', onClick: (e: MouseEvent) => { e.stopPropagation(); downloadOutputFile(result.outputFile.fileId, result.outputFile.fileName) } }, { default: () => '📥 ' + result.outputFile.fileName + (result.outputFile.sizeText ? ` (${result.outputFile.sizeText})` : '') }) : null,
          ]
        }),
        h(NSpace, null, {
          default: () => [
            h(NRadioGroup, {
              value: mode,
              size: 'small',
              onUpdateValue: (v: string) => { resultViewMode.value = { ...resultViewMode.value, [id]: v as any } }
            }, {
              default: () => [
                h(NRadioButton, { value: 'table' }, { default: () => '表格' }),
                h(NRadioButton, { value: 'json' }, { default: () => 'JSON' }),
              ]
            }),
            h(NButton, {
              size: 'tiny',
              onClick: (e: MouseEvent) => { e.stopPropagation(); copyResult(id) }
            }, { default: () => '📋 复制' }),
          ]
        }),
      ]),
      mode === 'table'
        ? h(MappedResultTable, { flowId: row.flowId, data: result.data, style: 'overflow-x:auto' })
        : h('pre', {
            style: 'margin:0;background:#fff;padding:8px;border-radius:4px;font-size:12px;max-height:300px;overflow:auto'
          }, typeof result === 'string' ? result : JSON.stringify(result, null, 2)),
    ])
  }
  if (execErrors.value[id]) {
    return h('div', { style: 'padding:12px 16px' }, [
      h('span', { style: 'color:#ff4d4f;font-size:12px' }, execErrors.value[id])
    ])
  }
  return h('div', { style: 'padding:12px 16px;color:#999;font-size:12px' }, '暂无结果')
}

const marketColumns = computed(() => [
  { title: '序号', key: '_idx', width: 60 },
  { title: '广场名称', key: 'name' },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  { title: '创建时间', key: 'createTime', width: 170, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 170, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: auth.isAdmin ? 300 : 100, render: (row: any) => {
    const btns = [h(NButton, { size: 'small', type: 'primary', onClick: () => enterMarket(row) }, { default: () => '进入' })]
    if (auth.isAdmin) {
      btns.push(h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }))
      btns.push(h(NButton, { size: 'small', type: 'info', onClick: () => openAssignUsers(row) }, { default: () => '人员' }))
      btns.push(h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除？'
      }))
    }
    return h(NSpace, null, { default: () => btns })
  }}
])

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

async function loadMarkets() {
  loading.value = true
  try {
    const res: any = await getMarketPage({ pageNum: page.value, pageSize: pageSize.value, name: searchName.value || undefined })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}

function openCreate() { isEdit.value = false; editId.value = null; form.value = { name: '', remark: '' }; showModal.value = true }
function openEdit(row: any) { isEdit.value = true; editId.value = row.id; form.value = { name: row.name, remark: row.remark || '' }; showModal.value = true }
async function handleSave() {
  try {
    if (isEdit.value) await updateMarket(editId.value!, form.value)
    else await createMarket(form.value)
    message.success(isEdit.value ? '修改成功' : '创建成功'); showModal.value = false; loadMarkets()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}
async function handleDelete(id: number) { await deleteMarket(id); message.success('删除成功'); loadMarkets() }

async function openAssignUsers(row: any) {
  userMarketId.value = row.id; userMarketName.value = row.name
  const [users, uids]: [any, any] = await Promise.all([getUserList(), getMarketUsers(row.id)])
  allUsers.value = (users || []).map((u: any) => ({ label: `${u.username}(${u.nickname || u.username})`, value: u.id, disabled: u.username === 'admin' }))
  selectedUserIds.value = uids || []; showUserModal.value = true
}
async function handleAssignUsers() {
  try { await assignMarketUsers(userMarketId.value!, selectedUserIds.value); message.success('人员分配成功'); showUserModal.value = false }
  catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function loadEnvs() {
  try { const res: any = await getEnvActive(); envOptions.value = (res || []).map((e: any) => ({ label: e.name, value: e.id })) } catch { /* ignore */ }
}

function enterMarket(row: any) {
  currentMarket.value = row
  router.replace({ query: { ...route.query, marketId: String(row.id), marketName: row.name } })
  loadMarketApps()
}
function backToList() {
  currentMarket.value = null
  const q = { ...route.query }; delete q.marketId; delete q.marketName
  router.replace({ query: q })
}

async function loadMarketApps() {
  appsLoading.value = true
  appPage.value = 1
  try {
    const res: any = await getMarketApps(currentMarket.value.id, { name: appSearchName.value || undefined, sharedBy: appSearchUser.value || undefined })
    marketApps.value = res || []
    const local: Record<number, string> = {}
    for (const r of res || []) { local[r.id] = inputParamsLocal.value[r.id] ?? r.inputParams ?? '' }
    inputParamsLocal.value = local
  } finally { appsLoading.value = false }
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
    await unshareApp(row.appId, currentMarket.value.id)
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
  resultViewMode.value = { ...resultViewMode.value, [appId]: 'table' }
  resultExpanded.value = { ...resultExpanded.value, [appId]: true }
  try {
    const file = uploadFiles.value[appId] || null
    let res: any
    if (file) {
      const form = new FormData(); form.append('file', file)
      res = await executeAppWithFile(appId, form, currentMarket.value?.id, selEnvId, controller.signal)
    } else {
      res = await executeApp(appId, currentMarket.value?.id, selEnvId, controller.signal)
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

async function copyResult(rowId: number) {
  const r = execResults.value[rowId]
  if (!r) return
  const target = r.data ?? r
  const text = typeof target === 'string' ? target : JSON.stringify(target, null, 2)
  try { await navigator.clipboard.writeText(text); message.success('已复制') } catch { message.error('复制失败') }
}

onMounted(async () => {
  await loadEnvs(); loadMarkets()
  // 刷新页面时从路由 query 恢复广场内页状态
  const mid = route.query.marketId
  if (mid) {
    currentMarket.value = { id: Number(mid), name: (route.query.marketName as string) || '广场' + mid }
    loadMarketApps()
  }
})

// 侧边栏点击“应用广场”菜单时 query 被清除 → 自动回到广场列表
watch(() => route.query.marketId, (mid) => {
  if (!mid && currentMarket.value) {
    currentMarket.value = null
  }
})

const expandedRowKeys = computed(() => groupedMarketApps.value.filter((r: any) => resultExpanded.value[r.appId]).map((r: any) => r.appId))
function onExpandedKeysChange(keys: Array<string | number>) {
  const r: Record<number, boolean> = {}
  groupedMarketApps.value.forEach((d: any) => r[d.appId] = keys.includes(d.appId))
  resultExpanded.value = r
}
</script>

<template>
  <div>
    <!-- 广场列表 -->
    <template v-if="!currentMarket">
      <NForm v-show="showSearch" :model="{searchName}" label-placement="left" style="margin-bottom:12px">
        <NSpace>
          <NFormItem label="名称"><NInput v-model:value="searchName" placeholder="请输入" clearable @keyup.enter="loadMarkets" /></NFormItem>
          <NButton type="primary" @click="loadMarkets">搜索</NButton>
          <NButton @click="searchName='';loadMarkets()">重置</NButton>
        </NSpace>
      </NForm>
      <NSpace justify="space-between" style="margin-bottom:12px">
        <NSpace>
          <NButton v-if="auth.isAdmin" type="primary" @click="openCreate">新建广场</NButton>
        </NSpace>
        <NButton text @click="showSearch=!showSearch">{{showSearch?'收起':'展开'}}搜索</NButton>
      </NSpace>
      <NDataTable :columns="marketColumns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; loadMarkets() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; loadMarkets() } }" remote bordered />
    </template>

    <!-- 广场内应用列表 -->
    <template v-else>
      <NSpace style="margin-bottom:12px"><NButton @click="backToList">← 返回广场列表</NButton><h3 style="margin:0">{{ currentMarket.name }}</h3></NSpace>
      <NForm :model="{appSearchName,appSearchUser}" label-placement="left" style="margin-bottom:12px">
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
    </template>

    <!-- CRUD Modal -->
    <NModal v-model:show="showModal" :key="String(isEdit)+String(editId)" preset="card" :title="isEdit?'编辑广场':'新建广场'" style="width:480px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80">
        <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="请输入广场名称" /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" type="textarea" /></NFormItem>
        <NSpace justify="end"><NButton @click="showModal=false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
      </NForm>
    </NModal>

    <!-- 分配人员 Modal -->
    <NModal v-model:show="showUserModal" preset="card" :title="`分配人员 — ${userMarketName}`" style="width:560px" :mask-closable="false">
      <NTransfer v-model:value="selectedUserIds" :options="allUsers" />
      <NSpace justify="end" style="margin-top:16px"><NButton @click="showUserModal=false">取消</NButton><NButton type="primary" @click="handleAssignUsers">确定</NButton></NSpace>
    </NModal>

    <!-- 参数表单填写 Modal -->
    <ParamFormModal v-model:show="showParamFormModal" :schema="paramFormSchema" @confirm="onParamFormConfirm" />
  </div>
</template>
