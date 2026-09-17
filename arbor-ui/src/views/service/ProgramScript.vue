<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NInput, NSelect, useMessage, NPopconfirm } from 'naive-ui'
import { getPsPage, deletePs, runPs } from '../../api/programScript'
import { formatDateTime } from '../../utils/format'
import ProgramScriptEditModal from '../../components/ProgramScriptEditModal.vue'
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
const showRun = ref(false)
const runResult = ref<any>(null)
const runScriptId = ref('')

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '脚本ID', key: 'scriptId', width: 160 },
  { title: '脚本名称', key: 'name' },
  { title: '备注', key: 'remark' },
  { title: '创建时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 310, render: (row: any) => {
    return h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => openRun(row) }, { default: () => '运行' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('program_script', row.id, row.name) }, { default: () => '追踪' }),
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
  try { const res: any = await getPsPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value }); data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 })); total.value = res.total } finally { loading.value = false }
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

async function handleDelete(id: number) { await deletePs(id); message.success('删除成功'); load() }

async function openRun(row: any) { runScriptId.value = row.scriptId; runResult.value = null; showRun.value = true }
async function handleRun() {
  try {
    const res: any = await runPs(runScriptId.value)
    runResult.value = res
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>程序脚本配置</h3>
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
    <ProgramScriptEditModal v-model:show="showModal" :script="editingScript" @saved="load" />

    <!-- 运行结果弹窗 -->
    <NModal v-model:show="showRun" preset="card" title="运行结果" style="width: 650px" :mask-closable="false">
      <div style="padding: 20px">
        <NButton type="primary" @click="handleRun" style="margin-bottom: 12px">执行</NButton>
        <pre v-if="runResult" style="background: #f5f5f5; padding: 12px; border-radius: 4px; max-height: 400px; overflow: auto">{{ JSON.stringify(runResult, null, 2) }}</pre>
      </div>
    </NModal>

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
