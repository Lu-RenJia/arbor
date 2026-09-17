<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NTag, NInput, NSelect, NDatePicker } from 'naive-ui'
import { getLogPage, getLogDetail } from '../../api/executionLog'
import { getUserList } from '../../api/userManage'
import { formatDateTime, formatDuration } from '../../utils/format'
import LogConsole from '../../components/LogConsole.vue'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()

const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const showDetail = ref(false)
const detail = ref<any>(null)
/** 节点状态清单默认收起，让日志成为详情主体（节点多的流程清单很长） */
const showNodeStatus = ref(false)

// ===== 搜索条件 =====
const searchFlowName = ref('')
const searchExecuteType = ref<string | null>(null)
const searchStatus = ref<string | null>(null)
const searchSource = ref<string | null>(null)
const searchOwnerId = ref<number | null>(null)
const searchExecutorId = ref<number | null>(null)
const searchTimeRange = ref<[number, number] | null>(null)

// ===== 用户列表（下拉选项） =====
const userOptions = ref<{ label: string; value: number }[]>([])

// ===== 固定选项 =====
const executeTypeOptions = [
  { label: '手动执行', value: 'MANUAL' },
  { label: '自动执行', value: 'AUTO' },
]
const statusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAIL' },
  { label: '流程停止', value: 'STOPPED' },
  { label: '已取消', value: 'CANCELLED' },
]
const sourceOptions = [
  { label: '测试流程', value: 'FLOW_TEST' },
  { label: '执行应用', value: 'APP' },
  { label: '脚本测试', value: 'SCRIPT_TEST' },
  { label: '手动执行', value: 'FLOW_MANUAL' },
  { label: '自动执行', value: 'FLOW_AUTO' },
]

// ===== 中文映射 =====
const EXECUTE_TYPE_LABELS: Record<string, string> = { MANUAL: '手动执行', AUTO: '自动执行' }
const SOURCE_LABELS: Record<string, string> = { FLOW_TEST: '测试流程', APP: '执行应用', FLOW_MANUAL: '手动执行', FLOW_AUTO: '自动执行', SCRIPT_TEST: '脚本测试' }
const STATUS_LABELS: Record<string, string> = { SUCCESS: '成功', FAIL: '失败', STOPPED: '流程停止', CANCELLED: '已取消' }

function executeTypeLabel(v: string): string { return EXECUTE_TYPE_LABELS[v] || v }
function sourceLabel(v: string): string { return SOURCE_LABELS[v] || v || '-' }
function statusLabel(status: string, stopType?: string): string {
  if (status === 'STOPPED') return stopType === 'NORMAL' ? '正常停止' : '异常停止'
  return STATUS_LABELS[status] || status
}
function statusTagType(status: string, stopType?: string): 'success' | 'error' | 'warning' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'STOPPED') return stopType === 'NORMAL' ? 'info' : 'warning'
  if (status === 'CANCELLED') return 'warning'
  return 'error'
}

/** 解析节点输出：output_result 已瘦身，只可能是 {"subFlowNodes":[...]} 或 null */
function parseSubFlowNodes(outputResult: string | null): any[] | null {
  if (!outputResult) return null
  try {
    const parsed = JSON.parse(outputResult)
    if (parsed && Array.isArray(parsed.subFlowNodes)) return parsed.subFlowNodes
  } catch { /* 历史数据可能不是 JSON，忽略 */ }
  return null
}

const NODE_TYPE_LABELS: Record<string, string> = {
  REF_API: '接口', REF_DB_SCRIPT: 'DB脚本', REF_PROGRAM_SCRIPT: '程序脚本',
  REF_FLOW: '子流程', REDIS: 'Redis', DB_QUERY: '数据库', GROOVY_SCRIPT: '代码',
  API_SEND: '接口发送', START: '开始', END: '结束',
}
function nodeTypeLabel(t: string): string { return NODE_TYPE_LABELS[t] || t }

const columns = [
  { title: '序号', key: '_idx', width: 55 },
  { title: '流程名称', key: 'flowName', ellipsis: { tooltip: true } },
  { title: '日志来源', key: 'source', width: 90, render: (row: any) => sourceLabel(row.source) },
  { title: '执行方式', key: 'executeType', width: 90, render: (row: any) => executeTypeLabel(row.executeType) },
  { title: '状态', key: 'status', width: 80, render: (row: any) => h(NTag, { size: 'small', type: statusTagType(row.status, row.stopType), bordered: false }, { default: () => statusLabel(row.status, row.stopType) }) },
  { title: '所属人', key: 'ownerName', width: 80, render: (row: any) => row.ownerName || '-' },
  { title: '执行人', key: 'executorName', width: 80, render: (row: any) => row.executorName || '-' },
  { title: '开始时间', key: 'startTime', width: 170, render: (row: any) => formatDateTime(row.startTime) },
  { title: '耗时', key: 'duration', width: 80, render: (row: any) => formatDuration(row.duration) },
  { title: '操作', key: 'actions', width: 70, render: (row: any) => {
    return h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openDetail(row.id) }, { default: () => '详情' })
  }},
]

const pagination = computed(() => ({
  page: page.value,
  pageSize: pageSize.value,
  itemCount: total.value,
  showSizePicker: true,
  pageSizes: [10, 20, 50, 100],
  prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? 0} 条`,
  onChange: (p: number) => { page.value = p; load() },
  onUpdatePageSize: (s: number) => { pageSize.value = s; page.value = 1; load() },
}))

async function load() {
  loading.value = true
  try {
    const params: any = { pageNum: page.value, pageSize: pageSize.value }
    if (searchFlowName.value) params.flowName = searchFlowName.value
    if (searchExecuteType.value) params.executeType = searchExecuteType.value
    if (searchStatus.value) params.status = searchStatus.value
    if (searchSource.value) params.source = searchSource.value
    if (searchOwnerId.value) params.ownerId = searchOwnerId.value
    if (searchExecutorId.value) params.executorId = searchExecutorId.value
    if (searchTimeRange.value) {
      params.startTime = formatTs(searchTimeRange.value[0])
      params.endTime = formatTs(searchTimeRange.value[1])
    }
    const res: any = await getLogPage(params)
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}

function formatTs(ts: number): string {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function handleSearch() { page.value = 1; load() }
function handleReset() {
  searchFlowName.value = ''
  searchExecuteType.value = null
  searchStatus.value = null
  searchSource.value = null
  searchOwnerId.value = null
  searchExecutorId.value = null
  searchTimeRange.value = null
  page.value = 1
  load()
}

async function openDetail(id: number) {
  const res: any = await getLogDetail(id); detail.value = res; showNodeStatus.value = false; showDetail.value = true
}

async function loadUsers() {
  try {
    const res: any = await getUserList()
    userOptions.value = (res || []).map((u: any) => ({ label: u.nickname || u.username, value: u.id }))
  } catch { /* ignore */ }
}

// 用户下拉只有管理员用得上（所属人/执行人筛选），而 /api/user/list 需要 user:list 权限，
// 普通用户调了必然 403，白白多一个失败请求
onMounted(() => { load(); if (auth.isAdmin) loadUsers() })
</script>

<template>
  <div>
    <h3 style="margin-bottom: 12px">数据执行日志</h3>

    <!-- 搜索栏 -->
    <NSpace style="margin-bottom: 12px" :wrap="true" :size="[12, 8]">
      <NInput v-model:value="searchFlowName" placeholder="流程名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
      <NSelect v-model:value="searchExecuteType" :options="executeTypeOptions" placeholder="执行方式" clearable style="width: 120px" />
      <NSelect v-model:value="searchSource" :options="sourceOptions" placeholder="日志来源" clearable style="width: 120px" />
      <NSelect v-model:value="searchStatus" :options="statusOptions" placeholder="状态" clearable style="width: 110px" />
      <!-- 所属人/执行人筛选仅管理员可见：普通用户只能看自己的日志，按他人筛选必然空结果 -->
      <NSelect v-if="auth.isAdmin" v-model:value="searchOwnerId" :options="userOptions" placeholder="所属人" clearable filterable style="width: 130px" />
      <NSelect v-if="auth.isAdmin" v-model:value="searchExecutorId" :options="userOptions" placeholder="执行人" clearable filterable style="width: 130px" />
      <NDatePicker v-model:value="searchTimeRange" type="datetimerange" clearable style="width: 340px" />
      <NButton type="primary" @click="handleSearch">搜索</NButton>
      <NButton @click="handleReset">重置</NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :pagination="pagination"
      :remote="true"
      bordered
      :scroll-x="1100"
    />

    <NModal v-model:show="showDetail" preset="card" title="执行详情" style="width: 900px" :mask-closable="false">
      <div v-if="detail" style="padding: 20px">
        <p>流程: <strong>{{ detail.flowName }}</strong> | 来源: {{ sourceLabel(detail.source) }} | 方式: {{ executeTypeLabel(detail.executeType) }} | 状态: <NTag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</NTag></p>
        <p>所属人: {{ detail.ownerName || '-' }} | 执行人: {{ detail.executorName || '-' }}</p>
        <p>耗时: {{ formatDuration(detail.duration) }} | {{ formatDateTime(detail.startTime) }} ~ {{ formatDateTime(detail.endTime) }}</p>
        <p v-if="detail.errorMessage" :style="{ color: detail.status === 'STOPPED' ? (detail.stopType === 'NORMAL' ? '#1677ff' : '#d48806') : 'red' }">{{ detail.status === 'STOPPED' ? (detail.stopType === 'NORMAL' ? '正常停止原因' : '异常停止原因') : '错误' }}: {{ detail.errorMessage }}</p>

        <h4 style="margin-top: 16px">执行日志</h4>
        <NButton size="small" quaternary @click="showNodeStatus = !showNodeStatus">
          {{ showNodeStatus ? '▾' : '▸' }} 节点状态 ({{ (detail.nodeLogs || []).length }})
        </NButton>
        <div v-if="showNodeStatus" style="margin: 8px 0 12px">
          <div v-for="nl in (detail.nodeLogs || [])" :key="nl.id" style="border: 1px solid #eee; border-radius: 4px; padding: 12px; margin-bottom: 8px">
            <div><NTag size="small">{{ nl.sortOrder }}</NTag> <strong>{{ nl.nodeName }}</strong> <NTag size="small" :type="statusTagType(nl.status, detail.stopType)">{{ statusLabel(nl.status, detail.stopType) }}</NTag> <span v-if="nl.duration" style="color:#999;font-size:12px">{{ formatDuration(nl.duration) }}</span></div>
            <!-- 子流程节点：展示内部节点状态（不再展示返回数据） -->
            <div v-if="parseSubFlowNodes(nl.outputResult)" style="margin-top: 8px; padding-left: 12px; border-left: 3px solid #9254de">
              <p style="color: #9254de; font-size: 12px; margin-bottom: 6px">子流程内部节点：</p>
              <div v-for="(sn, si) in parseSubFlowNodes(nl.outputResult)" :key="si" style="padding: 4px 0; font-size: 13px">
                <NTag size="tiny" :type="sn.status === 'SUCCESS' ? 'success' : 'error'">{{ sn.status === 'SUCCESS' ? '✓' : '✕' }}</NTag>
                <span style="margin-left: 6px">{{ sn.nodeName }}</span>
                <NTag size="tiny" :bordered="false" style="margin-left: 6px">{{ nodeTypeLabel(sn.nodeType) }}</NTag>
                <span v-if="sn.duration" style="color:#999;font-size:11px;margin-left:6px">{{ formatDuration(sn.duration) }}</span>
                <div v-if="sn.errorMessage" style="color:red;font-size:12px;margin-left:22px">{{ sn.errorMessage }}</div>
              </div>
            </div>
            <div v-if="nl.errorMessage" style="color: red; margin-top: 4px">错误: {{ nl.errorMessage }}</div>
          </div>
          <div v-if="!(detail.nodeLogs || []).length" style="color: #999; font-size: 12px; padding: 8px 0">无节点明细</div>
        </div>
        <!-- 日志控制台：与设计器底部「日志」页签复用同一组件 -->
        <LogConsole :logs="detail.scriptLogs || []" :max-height="320" />
      </div>
    </NModal>
  </div>
</template>
