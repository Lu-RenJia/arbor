<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NDataTable, NButton, NSpace, NPopconfirm, NTag, NModal, NDivider, NRadioGroup, NRadioButton, useMessage, NInputNumber, NSelect } from 'naive-ui'
import { getFlowPage, deleteFlow, executeFlow, cancelFlowExecution } from '../../api/flow'
import { toggleFlowSchedule } from '../../api/flowSchedule'
import { exportFlows } from '../../api/importExport'
import { downloadTextFile, buildPackageName } from '../../utils/download'
import { useAuthStore } from '../../stores/auth'
import { useEnvStore } from '../../stores/env'
import { getEnvActive } from '../../api/environment'
import { formatDateTime, formatDuration } from '../../utils/format'
import TraceDrawer from '../../components/TraceDrawer.vue'
import ImportPreviewModal from '../../components/ImportPreviewModal.vue'

const traceDrawer = ref<InstanceType<typeof TraceDrawer> | null>(null)

// 导入导出
const showImportModal = ref(false)
const exportingId = ref<number | null>(null)
async function handleExport(row: any) {
  exportingId.value = row.id
  try {
    const json: any = await exportFlows([row.id])
    downloadTextFile(json, buildPackageName(`flow-${row.name}`))
    message.success('导出成功')
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '导出失败')
  } finally { exportingId.value = null }
}

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchName = ref('')
const searchNodeCount = ref<number | null>(null)
const sortField = ref('update_time')
const sortOrder = ref('DESC')
const sortOptions = [
  { label: '修改时间↓', value: 'update_time:DESC' },
  { label: '修改时间↑', value: 'update_time:ASC' },
  { label: '创建时间↓', value: 'create_time:DESC' },
  { label: '创建时间↑', value: 'create_time:ASC' },
  { label: '节点数↓', value: 'node_count:DESC' },
  { label: '节点数↑', value: 'node_count:ASC' },
]

// 执行结果弹窗
const showResult = ref(false)
const execResult = ref<any>(null)
const resultViewMode = ref<'table' | 'json'>('table')
const executing = ref(false)
/** 执行中的流程ID + 取消控制器（点“取消执行”时中断 HTTP 等待，结果不再渲染） */
const executingFlowId = ref<number | null>(null)
const execController = ref<AbortController | null>(null)

// 环境选择（env store 统一管理，跨页面一致）
const envStore = useEnvStore()
const flowEnvId = ref<number | null>(envStore.flowDesignerEnvId)
const envOptions = ref<{ label: string; value: number }[]>([])
async function loadEnvs() {
  try { const res: any = await getEnvActive(); envOptions.value = (res || []).map((e: any) => ({ label: e.name, value: e.id })) } catch { /* ignore */ }
}
function onFlowEnvChange(val: number) {
  flowEnvId.value = val
  envStore.setFlowDesignerEnvId(val)
}

/** 节点类型中文映射 */
const typeLabels: Record<string, string> = {
  DB_QUERY: '数据库查询',
  GROOVY_SCRIPT: '代码脚本',
  API_SEND: '接口发送',
  REF_DB_SCRIPT: '数据库脚本',
  REF_PROGRAM_SCRIPT: '程序脚本',
  REF_API: '接口引用',
  START: '开始',
  END: '结束',
}

/** 节点结果数据JSON化 */
function formatData(data: any): string {
  if (data === null || data === undefined) return '-'
  if (typeof data === 'string') return data.length > 200 ? data.slice(0, 200) + '…' : data
  return JSON.stringify(data, null, 2)
}

const resultColumns = computed(() => {
  let list = execResult.value?.nodeDetailList
  if (!list || list.length === 0) {
    const nr = execResult.value?.nodeResults
    if (nr) list = Object.values(nr)
  }
  if (!list || list.length === 0) return []
  return [
    { title: '序号', key: '_idx', width: 50 },
    { title: '节点编码', key: 'nodeCode', width: 90 },
    { title: '节点名称', key: 'nodeName', width: 100 },
    { title: '类型', key: 'nodeType', width: 100, render: (r: any) => typeLabels[r.nodeType] || r.nodeType },
    { title: '状态', key: 'success', width: 72, render: (r: any) => r.status === 'STOPPED'
        ? h(NTag, { size: 'small', type: execResult.value?.stopType === 'NORMAL' ? 'info' : 'warning' }, { default: () => execResult.value?.stopType === 'NORMAL' ? '正常停止' : '流程停止' })
        : (r.success ? h(NTag, { size: 'small', type: 'success' }, { default: () => '成功' }) : h(NTag, { size: 'small', type: 'error' }, { default: () => '失败' })) },
    { title: '耗时', key: 'duration', width: 80, render: (r: any) => r.status === 'STOPPED' ? '-' : formatDuration(r.duration) },
    { title: '结果', key: 'data', ellipsis: { tooltip: true }, render: (r: any) => r.status === 'STOPPED' ? '-' : (r.success ? formatData(r.data) : (r.errorMessage || '-')) },
  ]
})

const resultData = computed(() => {
  // 优先用 nodeDetailList（新引擎格式），回退用 nodeResults map（旧格式）
  let list = execResult.value?.nodeDetailList
  if (!list || list.length === 0) {
    const nr = execResult.value?.nodeResults
    if (nr) {
      list = Object.values(nr)
    }
  }
  if (!list) return []
  return list.map((n: any, i: number) => ({ ...n, _idx: i + 1 }))
})

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '流程名称', key: 'name' },
  { title: '节点数', key: 'nodeCount', width: 80 },
  { title: '状态', key: 'status', width: 80, render: (row: any) => row.status === 1 ? '启用' : '关闭' },
  { title: '定时任务', key: 'cronExpr', width: 170, render: (row: any) => {
    if (!row.cronExpr) return h('span', { style: 'color:#bbb' }, '—')
    return h(NSpace, { size: 4, align: 'center', wrap: false }, { default: () => [
      h('code', { style: 'font-size:11px;color:#18a058' }, row.cronExpr),
      h(NTag, { size: 'tiny', type: row.scheduleEnabled === 1 ? 'success' : 'default', bordered: false }, { default: () => row.scheduleEnabled === 1 ? '启用' : '停用' })
    ]})
  }},
  { title: '创建时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 410, render: (row: any) => {
    return h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', type: 'primary', onClick: () => router.push(`/flow/designer/${row.id}`) }, { default: () => '设计' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('flow', row.id, row.name) }, { default: () => '追踪' }),
        h(NButton, { size: 'small', disabled: !row.cronExpr || !authStore.isAdmin, onClick: () => handleToggleSchedule(row) }, { default: () => row.scheduleEnabled === 1 ? '关闭' : '开启' }),
        h(NButton, { size: 'small', disabled: executing.value, onClick: () => handleExecute(row) }, { default: () => '执行' }),
        h(NButton, { size: 'small', loading: exportingId.value === row.id, onClick: () => handleExport(row) }, { default: () => '导出' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => `确认删除流程「${row.name}」？`
        })
      ]
    })
  }},
]

async function load() {
  loading.value = true
  try {
    const [field, order] = sortField.value && sortOrder.value
      ? [sortField.value, sortOrder.value]
      : ['update_time', 'DESC']
    const res: any = await getFlowPage({
      pageNum: page.value, pageSize: pageSize.value,
      name: searchName.value || undefined,
      nodeCount: searchNodeCount.value ?? undefined,
      sortField: field, sortOrder: order,
    })
    // 用分页序数作为序号，不用 id
    data.value = (res.list || []).map((row: any, i: number) => ({ ...row, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}

function handleSortChange(val: string) {
  const parts = val.split(':')
  sortField.value = parts[0]
  sortOrder.value = parts[1]
  page.value = 1
  load()
}

function handleCreate() {
  // 生成本地唯一名称（同用户不重名），不调后端
  const baseName = '新流程'
  const existingNames = data.value.map((f: any) => f.name)
  let name = baseName
  let n = 0
  while (existingNames.includes(name)) {
    n++
    name = `${baseName}(${n})`
  }
  router.push(`/flow/designer/new?name=${encodeURIComponent(name)}`)
}

async function handleDelete(id: number) { await deleteFlow(id); message.success('删除成功'); load() }
async function handleToggleSchedule(row: any) {
  const enable = row.scheduleEnabled !== 1
  await toggleFlowSchedule(row.id, enable)
  message.success(enable ? '定时任务已开启' : '定时任务已关闭')
  load()
}

async function handleExecute(row: any) {
  executing.value = true
  executingFlowId.value = row.id
  showResult.value = true
  execResult.value = null
  execController.value = new AbortController()
  try {
    let res: any = await executeFlow(row.id, undefined, flowEnvId.value ?? undefined, execController.value.signal)
    execResult.value = res
    resultViewMode.value = 'table'
  } catch (e: any) {
    // 用户主动取消：关闭弹窗不展示结果（后端已由 cancelExecute 通知止损）
    if (e?.code === 'ERR_CANCELED') { showResult.value = false }
    else {
      // 提取后端错误信息
      const msg = e?.response?.data?.message || e?.message || '执行失败'
      execResult.value = { flowName: row.name, executeType: 'MANUAL', success: false, duration: 0, startTime: null, errorMessage: msg, nodeResults: {}, nodeDetailList: [] }
    }
  } finally { executing.value = false; execController.value = null; executingFlowId.value = null }
}

/** 取消正在执行的流程：中断 HTTP 等待 + 通知后端在下一节点前终止 */
function cancelExecute() {
  execController.value?.abort()
  if (executingFlowId.value != null) cancelFlowExecution(executingFlowId.value).catch(() => { /* 忽略 */ })
  message.info('已取消执行')
}

onMounted(() => { loadEnvs(); load() })
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>数据加工流程</h3>
      <NSpace>
        <NInput v-model:value="searchName" placeholder="搜索流程名称" style="width: 160px" @keyup.enter="load" clearable />
        <NInputNumber v-model:value="searchNodeCount" placeholder="节点数" style="width: 90px" :min="0" clearable @keyup.enter="load" />
        <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
        <NButton @click="page = 1; load()">搜索</NButton>
        <NDivider vertical />
        <span style="font-size:13px;color:#666">环境：</span>
        <NRadioGroup v-model:value="flowEnvId" size="small" @update-value="onFlowEnvChange">
          <NRadioButton v-for="e in envOptions" :key="e.value" :value="e.value">{{ e.label }}</NRadioButton>
        </NRadioGroup>
        <NButton type="primary" @click="handleCreate">新增流程</NButton>
        <NButton @click="showImportModal = true">导入流程</NButton>
      </NSpace>
    </NSpace>
    <NDataTable
      :columns="columns" :data="data" :loading="loading"
      :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote
      bordered
    />
  </div>

  <!-- 执行结果弹窗 -->
  <NModal v-model:show="showResult" preset="card" title="流程执行结果" style="width: 950px" :mask-closable="false">
    <div v-if="executing" style="text-align: center; padding: 40px; color: #999">
      执行中…
      <div style="margin-top: 12px"><NButton size="small" type="error" @click="cancelExecute">取消执行</NButton></div>
    </div>
    <template v-else-if="execResult">
      <!-- 汇总信息 -->
      <div style="display: flex; gap: 24px; margin-bottom: 12px; flex-wrap: wrap; font-size: 14px">
        <span><strong>流程名称：</strong>{{ execResult.flowName }}</span>
        <span><strong>执行方式：</strong>{{ execResult.executeType === 'MANUAL' ? '手动' : '自动' }}</span>
        <span>
          <strong>状态：</strong>
          <NTag :type="execResult.status === 'STOPPED' ? (execResult.stopType === 'NORMAL' ? 'info' : 'warning') : (execResult.success ? 'success' : 'error')" size="small" style="vertical-align: middle">
            {{ execResult.status === 'STOPPED' ? (execResult.stopType === 'NORMAL' ? '正常停止' : '流程停止') : (execResult.success ? '成功' : '失败') }}
          </NTag>
        </span>
        <span><strong>总耗时：</strong>{{ formatDuration(execResult.duration) }}</span>
        <span><strong>开始时间：</strong>{{ formatDateTime(execResult.startTime) }}</span>
        <span v-if="execResult.endTime"><strong>结束时间：</strong>{{ formatDateTime(execResult.endTime) }}</span>
      </div>
      <div v-if="execResult.status === 'STOPPED' && execResult.stopMessage"
        :style="{
          padding: '8px 12px',
          background: execResult.stopType === 'NORMAL' ? '#e8f4fd' : '#fffbe6',
          border: execResult.stopType === 'NORMAL' ? '1px solid #91caff' : '1px solid #ffe58f',
          borderRadius: '4px',
          marginBottom: '12px',
          fontSize: '13px',
          color: execResult.stopType === 'NORMAL' ? '#1677ff' : '#d48806'
        }">
        {{ execResult.stopType === 'NORMAL' ? '✅' : '⏸' }} {{ execResult.stopType === 'NORMAL' ? '正常停止原因' : '停止原因' }}：{{ execResult.stopMessage }}
      </div>
      <div v-if="execResult.errorMessage" style="padding: 8px 12px; background: #fff2f0; border: 1px solid #ffccc7; border-radius: 4px; margin-bottom: 12px; font-size: 13px; color: #cf1322">
        {{ execResult.errorMessage }}
      </div>

      <NDivider style="margin: 8px 0" />

      <!-- 视图切换 + 节点详情 -->
      <NSpace justify="space-between" align="center" style="margin-bottom: 8px">
        <span style="font-weight: 600">节点执行详情</span>
        <NRadioGroup v-model:value="resultViewMode" size="small">
          <NRadioButton value="table">表格</NRadioButton>
          <NRadioButton value="json">JSON</NRadioButton>
        </NRadioGroup>
      </NSpace>

      <NDataTable
        v-if="resultViewMode === 'table'"
        :columns="resultColumns"
        :data="resultData"
        :max-height="400"
        :single-line="false"
        size="small"
        bordered
      />
      <pre v-else style="margin: 0; max-height: 400px; overflow: auto; background: #f5f5f5; padding: 12px; border-radius: 6px; font-size: 12px">{{ JSON.stringify(execResult, null, 2) }}</pre>
    </template>
    <div v-else style="text-align: center; padding: 40px; color: #999">暂无结果</div>
  </NModal>

  <TraceDrawer ref="traceDrawer" />

  <!-- 导入弹窗 -->
  <ImportPreviewModal v-model:show="showImportModal" @done="load" />
</template>
