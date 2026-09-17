<script setup lang="ts">
import { ref, onMounted, h, nextTick } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NTag, useMessage, NPopconfirm, NTreeSelect, NInputNumber } from 'naive-ui'
import { getDeptList, getDeptTree, createDept, updateDept, deleteDept } from '../../api/department'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const deptList = ref<any[]>([])
const loading = ref(false)
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', parentId: 0 as number | null, leader: '', phone: '', email: '', sortOrder: 0, status: '0' })
const treeOptions = ref<any[]>([])
const isExpandAll = ref(true)
const refreshTable = ref(true)
const showSearch = ref(true)
const deptName = ref('')
const statusFilter = ref('')

const columns = [
  { title: '部门名称', key: 'name', width: 200 },
  { title: '排序', key: 'sortOrder', width: 70 },
  { title: '负责人', key: 'leader', width: 100 },
  { title: '电话', key: 'phone', width: 120 },
  { title: '状态', key: 'status', width: 80, render: (row: any) => row.status === '0' ? h(NTag, { type: 'success', size: 'small' }, { default: () => '正常' }) : h(NTag, { type: 'error', size: 'small' }, { default: () => '停用' }) },
  { title: '创建时间', key: 'createTime', width: 170, render: (row: any) => formatDateTime(row.createTime) },
  { title: '操作', key: 'actions', width: 180, render: (row: any) => h(NSpace, null, { default: () => [
    h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '修改' }),
    h(NButton, { size: 'small', type: 'primary', onClick: () => openCreate(row) }, { default: () => '新增' }),
    h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
      trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
      default: () => '确认删除该部门？'
    })
  ]})},
]

function buildTree(arr: any[]): any[] {
  const map = new Map<number, any>(); const roots: any[] = []
  for (const d of arr) { d.children = []; map.set(d.id, d) }
  for (const d of arr) {
    if (d.parentId && map.has(d.parentId)) map.get(d.parentId)!.children.push(d)
    else roots.push(d)
  }
  return roots
}

function filterTree(arr: any[]): any[] {
  return arr.filter(d => {
    if (deptName.value && !d.name.includes(deptName.value)) return (d.children = filterTree(d.children || [])).length > 0
    if (statusFilter.value && d.status !== statusFilter.value) return (d.children = filterTree(d.children || [])).length > 0
    if (d.children) d.children = filterTree(d.children)
    return true
  })
}

async function load() {
  loading.value = true
  try {
    const res: any = await getDeptList()
    let tree = buildTree(res || [])
    if (deptName.value || statusFilter.value) tree = filterTree(JSON.parse(JSON.stringify(tree)))
    deptList.value = tree
  } finally { loading.value = false }
}

async function loadTree() {
  const res: any = await getDeptTree()
  treeOptions.value = flattenTree(res || [])
}

function flattenTree(nodes: any[], prefix = ''): any[] {
  const result: any[] = []
  for (const n of nodes) {
    result.push({ label: prefix + n.name, value: n.id, ...n })
    if (n.children) result.push(...flattenTree(n.children, prefix + '　'))
  }
  return result
}

function toggleExpandAll() {
  refreshTable.value = false; isExpandAll.value = !isExpandAll.value
  nextTick(() => { refreshTable.value = true })
}

function openCreate(row?: any) {
  isEdit.value = false; editId.value = null
  form.value = { name: '', parentId: row ? row.id : 0, leader: '', phone: '', email: '', sortOrder: 0, status: '0' }
  loadTree(); showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { name: row.name, parentId: row.parentId, leader: row.leader || '', phone: row.phone || '', email: row.email || '', sortOrder: row.sortOrder || 0, status: row.status || '0' }
  loadTree(); showModal.value = true
}

async function handleSave() {
  try {
    const payload: any = { ...form.value, parentId: form.value.parentId || 0 }
    if (isEdit.value) await updateDept(editId.value!, payload)
    else await createDept(payload)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDelete(id: number) { await deleteDept(id); message.success('删除成功'); load() }

onMounted(load)
</script>

<template>
  <div>
    <NForm v-show="showSearch" label-placement="left" label-width="80" style="margin-bottom:12px">
      <NSpace>
        <NFormItem label="部门名称"><NInput v-model:value="deptName" placeholder="请输入" clearable /></NFormItem>
        <NFormItem label="状态"><NSelect v-model:value="statusFilter" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" placeholder="全部" clearable style="width:120px" /></NFormItem>
        <NButton type="primary" @click="load">搜索</NButton>
        <NButton @click="deptName='';statusFilter='';load()">重置</NButton>
      </NSpace>
    </NForm>

    <NSpace justify="space-between" style="margin-bottom:12px">
      <NSpace>
        <NButton type="primary" @click="openCreate()">新增</NButton>
        <NButton @click="toggleExpandAll">{{ isExpandAll ? '折叠全部' : '展开全部' }}</NButton>
      </NSpace>
      <NButton text @click="showSearch = !showSearch">{{ showSearch ? '收起搜索' : '展开搜索' }}</NButton>
    </NSpace>

    <NDataTable v-if="refreshTable" :columns="columns" :data="deptList" :loading="loading" :row-key="(row: any) => row.id" children-key="children" :default-expand-all="isExpandAll" bordered />

    <NModal v-model:show="showModal" :key="String(isEdit)+String(editId)" preset="card" :title="isEdit?'编辑部门':'新增部门'" style="width:520px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="100">
        <NFormItem label="上级部门"><NTreeSelect v-model:value="form.parentId" :options="treeOptions" placeholder="无（顶级部门）" clearable /></NFormItem>
        <NFormItem label="部门名称" required><NInput v-model:value="form.name" placeholder="请输入部门名称" /></NFormItem>
        <NFormItem label="负责人"><NInput v-model:value="form.leader" placeholder="请输入负责人" /></NFormItem>
        <NFormItem label="联系电话"><NInput v-model:value="form.phone" placeholder="请输入联系电话" /></NFormItem>
        <NFormItem label="邮箱"><NInput v-model:value="form.email" placeholder="请输入邮箱" /></NFormItem>
        <NFormItem label="排序"><NInputNumber v-model:value="form.sortOrder" :min="0" style="width:100%" /></NFormItem>
        <NFormItem label="状态"><NSelect v-model:value="form.status" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" /></NFormItem>
        <NSpace justify="end"><NButton @click="showModal=false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
      </NForm>
    </NModal>
  </div>
</template>
