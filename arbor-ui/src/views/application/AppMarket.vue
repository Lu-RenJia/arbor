<script setup lang="ts">
/**
 * 应用广场：广场列表 + 广场内应用详情。
 * 详情视图已拆到 MarketAppsPanel.vue；本文件只负责广场列表、广场 CRUD 和路由 query 状态。
 */
import { ref, onMounted, h, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, useMessage, NPopconfirm, NTransfer } from 'naive-ui'
import { getMarketPage, createMarket, updateMarket, deleteMarket, getMarketUsers, assignMarketUsers } from '../../api/appMarket'
import { getUserList } from '../../api/userManage'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import MarketAppsPanel from './MarketAppsPanel.vue'

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

// 当前广场（非空时展示 MarketAppsPanel）
const currentMarket = ref<any>(null)

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

function enterMarket(row: any) {
  currentMarket.value = row
  router.replace({ query: { ...route.query, marketId: String(row.id), marketName: row.name } })
}
function backToList() {
  currentMarket.value = null
  const q = { ...route.query }; delete q.marketId; delete q.marketName
  router.replace({ query: q })
}

onMounted(async () => {
  loadMarkets()
  // 刷新页面时从路由 query 恢复广场内页状态（详情数据由 MarketAppsPanel 加载）
  const mid = route.query.marketId
  if (mid) {
    currentMarket.value = { id: Number(mid), name: (route.query.marketName as string) || '广场' + mid }
  }
})

// 侧边栏点击“应用广场”菜单时 query 被清除 → 自动回到广场列表
watch(() => route.query.marketId, (mid) => {
  if (!mid && currentMarket.value) {
    currentMarket.value = null
  }
})
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
    <MarketAppsPanel v-else :market="currentMarket" @back="backToList" />

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
  </div>
</template>
