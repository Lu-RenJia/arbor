<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NInput, NSelect, useMessage, NPopconfirm } from 'naive-ui'
import { getRedisScriptPage, deleteRedisScript, testRedisScript } from '../../api/redisScript'
import { formatDateTime } from '../../utils/format'
import TraceDrawer from '../../components/TraceDrawer.vue'
import RedisScriptEditModal from '../../components/RedisScriptEditModal.vue'

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
/** 新建/编辑弹窗（与流程设计器内联编辑共用同一组件，表单与校验完全一致） */
const showModal = ref(false)
const editingScript = ref<any>(null)

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '名称', key: 'name' },
  { title: '命令', key: 'command', width: 90 },
  { title: 'Redis 数据源', key: 'datasourceName', width: 160, ellipsis: { tooltip: true } },
  // 展示完整 key（{业务前缀}:{redisKey}）而非裸 redisKey：列表上看到的应当就是
  // Redis 里真实存在的那个 key，这是「方便溯源」的直接落点
  { title: 'Key', key: 'fullKey', ellipsis: { tooltip: true }, render: (row: any) => row.fullKey || row.redisKey },
  { title: '备注', key: 'remark' },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 270, render: (row: any) => h(NSpace, null, {
    default: () => [
      h(NButton, { size: 'small', onClick: () => handleTest(row.id) }, { default: () => '测试' }),
      h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('redis_script', row.id, row.name) }, { default: () => '追踪' }),
      h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
      h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除？'
      })
    ]
  })},
]

function fmtResult(v: any): string {
  if (v === null || v === undefined) return '(nil) 未命中'
  return typeof v === 'string' ? v : JSON.stringify(v, null, 2)
}

async function load() { loading.value = true; try { const res: any = await getRedisScriptPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value }); data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 })); total.value = res.total } finally { loading.value = false } }
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }
function openCreate() { editingScript.value = null; showModal.value = true }
function openEdit(row: any) { editingScript.value = row; showModal.value = true }
async function handleDelete(id: number) { await deleteRedisScript(id); message.success('删除成功'); load() }

/** 表格行测试：执行并展示返回值 */
async function handleTest(id: number) {
  try {
    const res: any = await testRedisScript(id)
    message.info('执行结果: ' + fmtResult(res))
  } catch (e: any) { message.error(e?.message || '测试失败') }
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
          <h3>Redis 配置</h3>
          <NSpace>
            <NInput v-model:value="searchKeyword" placeholder="搜索配置名称" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
            <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
            <NButton @click="page = 1; load()">搜索</NButton>
            <NButton type="primary" @click="openCreate">新增配置</NButton>
          </NSpace>
        </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered />
    <!-- 新建/编辑：与流程设计器内联编辑共用同一弹窗组件（业务前缀必选） -->
    <RedisScriptEditModal v-model:show="showModal" :script="editingScript" @saved="load" />

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
