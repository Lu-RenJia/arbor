<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NInput, NSelect, useMessage, NPopconfirm } from 'naive-ui'
import { getApiPage, deleteApi, testApi } from '../../api/apiConfig'
import { formatDateTime } from '../../utils/format'
import { downloadFileById } from '../../api/file'
import TraceDrawer from '../../components/TraceDrawer.vue'
import ApiConfigEditModal from '../../components/ApiConfigEditModal.vue'

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
const editingApi = ref<any>(null)

// 已保存接口测试弹窗
const showTest = ref(false)
const testSavedResult = ref<any>(null)
const testApiId = ref<number | null>(null)

const columns = [
  { title: '序号', key: '_idx', width: 60 }, { title: '接口ID', key: 'apiId', width: 150 },
  { title: '名称', key: 'name' }, { title: '方法', key: 'method', width: 80 },
  { title: '地址', key: 'url', ellipsis: { tooltip: true } }, { title: '备注', key: 'remark' },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 310, render: (row: any) => {
    return h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => openTest(row) }, { default: () => '测试' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('api_config', row.id, row.name) }, { default: () => '追踪' }),
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
  try { const res: any = await getApiPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value }); data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 })); total.value = res.total } finally { loading.value = false }
}
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }

function openCreate() { editingApi.value = null; showModal.value = true }
function openEdit(row: any) { editingApi.value = row; showModal.value = true }
function onSaved() { load() }

async function handleDelete(id: number) { await deleteApi(id); message.success('删除成功'); load() }

async function openTest(row: any) { testApiId.value = row.id; testSavedResult.value = null; showTest.value = true }
async function handleTest() { try { const res: any = await testApi(testApiId.value!); testSavedResult.value = res } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '测试失败') } }

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>接口配置</h3>
      <NSpace>
        <NInput v-model:value="searchKeyword" placeholder="搜索接口名称" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
        <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
        <NButton @click="page = 1; load()">搜索</NButton>
        <NButton type="primary" @click="openCreate">新增接口</NButton>
      </NSpace>
    </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered>
    </NDataTable>

    <!-- 新建/编辑接口（复用组件，与设计器内联编辑一致） -->
    <ApiConfigEditModal v-model:show="showModal" :api="editingApi" @saved="onSaved" />

    <!-- 已保存接口测试弹窗 -->
    <NModal v-model:show="showTest" preset="card" title="测试结果" style="width: 650px" :mask-closable="false">
      <div style="padding: 20px">
        <NButton type="primary" @click="handleTest" style="margin-bottom: 12px">发送请求</NButton>
        <div v-if="testSavedResult">
          <p>状态码: <strong>{{ testSavedResult.statusCode }}</strong> | 耗时: <strong>{{ testSavedResult.responseTime }}</strong>
            <NButton v-if="testSavedResult.fileId" size="tiny" type="info" style="margin-left: 8px" @click="downloadFileById(testSavedResult.fileId)">📥 {{ testSavedResult.fileName || '下载文件' }}</NButton>
          </p>
          <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; max-height: 400px; overflow: auto">{{ typeof testSavedResult.body === 'string' ? testSavedResult.body : JSON.stringify(testSavedResult.body, null, 2) }}</pre>
        </div>
      </div>
    </NModal>

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
