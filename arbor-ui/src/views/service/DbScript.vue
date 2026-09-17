<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NInput, NSelect, NInputNumber, useMessage, NPopconfirm, NRadioGroup, NRadioButton, NPagination } from 'naive-ui'
import { getDbScriptPage, deleteDbScript, executeDbScript } from '../../api/dbScript'
import { formatDateTime, formatDuration } from '../../utils/format'
import DbScriptEditModal from '../../components/DbScriptEditModal.vue'
import TraceDrawer from '../../components/TraceDrawer.vue'

const traceDrawer = ref<InstanceType<typeof TraceDrawer> | null>(null)

const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
// 全站统一：默认按修改时间倒序
const sortField = ref('update_time')
const sortOrder = ref('DESC')
const sortOptions = [
  { label: '修改时间↓', value: 'update_time:DESC' },
  { label: '修改时间↑', value: 'update_time:ASC' },
  { label: '创建时间↓', value: 'create_time:DESC' },
  { label: '创建时间↑', value: 'create_time:ASC' },
]
// 新建/编辑弹窗（复用独立组件，与流程设计器内联编辑保持一致）
const showModal = ref(false)
const editingScript = ref<any>(null)

const showExec = ref(false)
const execScriptId = ref(0)
const execScriptName = ref('')
const execDsId = ref<number | null>(null)
const execResult = ref<any>(null)
const execTotal = ref(0)
const execPage = ref(1)
const execPageSize = ref(10)
const execViewMode = ref<'table' | 'json'>('table')
const execLoading = ref(false)
const execError = ref('')
/** 执行耗时（毫秒，前端计时） */
const execDuration = ref<number | null>(null)

// 页面大小选项：含预设值和"自定义"入口
const PAGE_SIZE_OPTIONS = [5, 10, 20, 50, 100, 200]

/** 将查询结果转换为表格列定义 */
function buildResultColumns(result: any[]): any[] {
  if (!result || result.length === 0) return []
  return Object.keys(result[0]).map(key => ({ title: key, key, ellipsis: { tooltip: true }, width: 140, resizable: true }))
}

function isTableLike(result: any[]): boolean {
  if (!result || result.length === 0) return false
  return Array.isArray(result) && typeof result[0] === 'object' && result[0] !== null
}

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '脚本名称', key: 'name' },
  { title: '数据源', key: 'datasourceName', width: 160, ellipsis: { tooltip: true } },
  { title: '备注', key: 'remark' },
  { title: '创建时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 330, render: (row: any) => {
    return h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => openExecute(row) }, { default: () => '执行预览' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('db_script', row.id, row.name) }, { default: () => '追踪' }),
        h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确认删除？'
        })
      ]
    })
  }},
]

async function load() {
  loading.value = true
  try { const res: any = await getDbScriptPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value }); data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 })); total.value = res.total } finally { loading.value = false }
}
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }

function openCreate() {
  editingScript.value = null
  showModal.value = true
}

function openEdit(row: any) {
  editingScript.value = row
  showModal.value = true
}

async function handleDelete(id: number) { await deleteDbScript(id); message.success('删除成功'); load() }

/** 执行预览：用脚本绑定的数据源直接执行，弹窗只展示结果 */
async function openExecute(row: any) {
  if (!row.datasourceId) {
    message.warning(`脚本「${row.name}」未绑定数据源，请先编辑脚本选择数据源`)
    return
  }
  execScriptId.value = row.id
  execScriptName.value = row.name
  execDsId.value = row.datasourceId
  execResult.value = null; execError.value = ''; execViewMode.value = 'table'
  execPage.value = 1; execPageSize.value = 10; execTotal.value = 0; execDuration.value = null
  showExec.value = true
  handleExecute(1, 10)
}

async function handleExecute(pageNum?: number, pageSize?: number) {
  if (!execDsId.value) { message.warning('该脚本未绑定数据源'); return }
  execLoading.value = true
  execError.value = ''
  const pn = pageNum ?? execPage.value
  const ps = pageSize ?? execPageSize.value
  const start = Date.now()
  try {
    const res: any = await executeDbScript(execScriptId.value, execDsId.value, pn, ps)
    execDuration.value = Date.now() - start
    execResult.value = res.list
    execTotal.value = res.total
    execPage.value = pn
    execPageSize.value = ps
  } catch {
    // 拦截器已弹出具体错误，这里在弹窗内保留失败状态展示
    execError.value = '执行失败'
    execResult.value = null
  }
  finally { execLoading.value = false }
}

function handleExecPageSizeChange(v: number | null) {
  const size = v ?? execPageSize.value
  execPageSize.value = size
  execPage.value = 1
  handleExecute(1, size)
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
          <h3>数据库脚本配置</h3>
          <NSpace>
            <NInput v-model:value="searchKeyword" placeholder="搜索脚本名称" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
            <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
            <NButton @click="page = 1; load()">搜索</NButton>
            <NButton type="primary" @click="openCreate">新增脚本</NButton>
          </NSpace>
        </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered>
    </NDataTable>

    <!-- 新增/编辑弹窗（与流程设计器内联编辑共用组件） -->
    <DbScriptEditModal v-model:show="showModal" :script="editingScript" @saved="load" />

    <!-- 执行预览弹窗（打开即用脚本绑定的数据源执行，只展示结果） -->
    <NModal v-model:show="showExec" preset="card" :title="'执行预览 — ' + execScriptName" style="width: 950px" :mask-closable="false">
      <div style="padding: 8px 0">
        <div v-if="execLoading" style="text-align: center; padding: 40px; color: #999">执行中…</div>
        <div v-else-if="execError" style="padding: 12px; background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px">
          <div style="font-weight: 600; color: #ff4d4f">✗ 执行失败</div>
        </div>
        <div v-else-if="execResult" style="padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px">
          <NSpace justify="space-between" align="center" style="margin-bottom: 8px">
            <span style="font-weight: 600; color: #52c41a">✓ 执行成功（共 {{ execTotal }} 条，耗时 {{ formatDuration(execDuration ?? 0) }}）</span>
            <NRadioGroup v-model:value="execViewMode" size="small">
              <NRadioButton value="table">表格</NRadioButton>
              <NRadioButton value="json">JSON</NRadioButton>
            </NRadioGroup>
          </NSpace>
          <div v-if="execViewMode === 'table' && isTableLike(execResult)" style="overflow-x: auto; max-width: 100%">
            <NDataTable
              :columns="buildResultColumns(execResult)"
              :data="execResult"
              :max-height="400"
              :single-line="false"
              size="small"
              bordered
              :scroll-x="Math.max(buildResultColumns(execResult).length * 140, 800)"
            />
          </div>
          <pre v-else style="margin: 0; background: #fff; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 400px; overflow: auto">{{ JSON.stringify(execResult, null, 2) }}</pre>
          <!-- 分页 -->
          <NSpace v-if="execViewMode === 'table' && execTotal > 0" justify="space-between" align="center" style="margin-top: 8px">
            <NSpace align="center" :size="4">
              <span style="font-size: 12px; color: #999">每页</span>
              <NInputNumber v-model:value="execPageSize" :min="1" :max="200" size="small" style="width: 80px" @update:value="handleExecPageSizeChange" />
              <span style="font-size: 12px; color: #999">条</span>
              <span v-for="n in PAGE_SIZE_OPTIONS" :key="n" style="font-size: 12px; color: #2080f0; cursor: pointer; padding: 0 4px" @click="handleExecPageSizeChange(n)">{{ n }}</span>
            </NSpace>
            <NPagination v-model:page="execPage" :page-size="execPageSize" :item-count="execTotal" size="small" @update:page="(p: number) => handleExecute(p, execPageSize)" />
          </NSpace>
        </div>
      </div>
    </NModal>

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
