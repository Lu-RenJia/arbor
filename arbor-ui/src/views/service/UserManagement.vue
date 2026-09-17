<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NTag, useMessage, NPopconfirm, NRow, NCol, NRadioGroup, NRadio, NSwitch, NCheckboxGroup, NCheckbox } from 'naive-ui'
import { getUserPage, createUser, updateUser, deleteUser, resetPassword, changeUserStatus, getUserAuthRole, assignUserRoles } from '../../api/userManage'
import { getDeptList } from '../../api/department'
import { getRoleList } from '../../api/role'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const showSearch = ref(true)
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ username: '', password: '', nickname: '', email: '', phone: '', sex: '2', role: 'USER', departmentId: null as number | null, status: '0' })
const queryParams = ref({ username: '', phone: '', status: '' })
const deptOptions = ref<{ label: string; value: number }[]>([])
const roleOptions = ref<{ label: string; value: string }[]>([])

// 重置密码
const showPwdModal = ref(false)
const pwdUserId = ref<number | null>(null)
const pwdUsername = ref('')
const newPassword = ref('')

// 分配角色
const showRoleModal = ref(false)
const roleUserId = ref<number | null>(null)
const roleUsername = ref('')
const allRoleOptions = ref<{ label: string; value: number }[]>([])
const checkedRoleIds = ref<number[]>([])

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '用户名', key: 'username' },
  { title: '昵称', key: 'nickname' },
  { title: '手机号', key: 'phone', width: 120, render: (row: any) => row.phone || '-' },
  { title: '角色', key: 'role', width: 100, render: (row: any) => {
    const name = roleOptions.value.find(r => r.value === row.role)?.label || row.role
    return row.role === 'ADMIN' ? h(NTag, { type: 'error', size: 'small' }, { default: () => name }) : h(NTag, { type: 'info', size: 'small' }, { default: () => name })
  } },
  { title: '部门', key: 'departmentName' },
  { title: '状态', key: 'status', width: 90, render: (row: any) => h(NSwitch, { value: row.status === '0', onUpdateValue: (v: boolean) => handleStatusChange(row, v), disabled: row.username === 'admin' }) },
  { title: '创建时间', key: 'createTime', width: 170, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 170, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 260, render: (row: any) => h(NSpace, null, { default: () => [
    h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
    h(NButton, { size: 'small', type: 'info', onClick: () => openAssignRole(row) }, { default: () => '分配角色' }),
    h(NButton, { size: 'small', type: 'warning', onClick: () => openResetPwd(row) }, { default: () => '重置密码' }),
    h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
      trigger: () => h(NButton, { size: 'small', type: 'error', disabled: row.username === 'admin', 'v-has-permi': ['user:delete'] }, { default: () => '删除' }),
      default: () => '确认删除？'
    })
  ]})},
]

async function load() {
  loading.value = true
  try {
    const res: any = await getUserPage({ ...queryParams.value, pageNum: page.value, pageSize: pageSize.value })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}

async function loadOpts() {
  try {
    const [depts, roles]: [any, any] = await Promise.all([getDeptList(), getRoleList()])
    deptOptions.value = (depts || []).map((d: any) => ({ label: d.name, value: d.id }))
    roleOptions.value = (roles || []).map((r: any) => ({ label: r.name, value: r.code }))
  } catch {/* ignore */}
}

function handleQuery() { page.value = 1; load() }
function resetQuery() { queryParams.value = { username: '', phone: '', status: '' }; handleQuery() }

function openCreate() {
  isEdit.value = false; editId.value = null
  form.value = { username: '', password: '', nickname: '', email: '', phone: '', sex: '2', role: 'USER', departmentId: null, status: '0' }
  loadOpts(); showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { username: row.username, password: '', nickname: row.nickname || '', email: row.email || '', phone: row.phone || '', sex: row.sex || '2', role: row.role, departmentId: row.departmentId, status: row.status }
  loadOpts(); showModal.value = true
}

async function handleSave() {
  if (!isEdit.value && form.value.password && form.value.password.length < 6) {
    message.error('密码长度不能少于6位'); return
  }
  try {
    const payload = { nickname: form.value.nickname, email: form.value.email, phone: form.value.phone, sex: form.value.sex, role: form.value.role, departmentId: form.value.departmentId, status: form.value.status }
    if (isEdit.value) await updateUser(editId.value!, payload)
    else await createUser({ ...form.value, departmentId: form.value.departmentId ?? undefined })
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDelete(id: number) { await deleteUser(id); message.success('删除成功'); load() }

function openResetPwd(row: any) {
  pwdUserId.value = row.id; pwdUsername.value = row.username; newPassword.value = ''
  showPwdModal.value = true
}

async function handleResetPwd() {
  if (!newPassword.value) { message.error('请输入新密码'); return }
  if (newPassword.value.length < 6) { message.error('密码长度不能少于6位'); return }
  try {
    await resetPassword(pwdUserId.value!, newPassword.value)
    message.success('密码重置成功'); showPwdModal.value = false
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '重置失败') }
}

async function handleStatusChange(row: any, v: boolean) {
  const newStatus = v ? '0' : '1'
  try {
    await changeUserStatus(row.id, newStatus)
    row.status = newStatus
    message.success(v ? '已启用' : '已停用')
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function openAssignRole(row: any) {
  roleUserId.value = row.id; roleUsername.value = row.username
  const [roles, checked]: [any, any] = await Promise.all([
    getRoleList(), getUserAuthRole(row.id)
  ])
  allRoleOptions.value = (roles || []).map((r: any) => ({ label: r.name, value: r.id }))
  checkedRoleIds.value = checked || []
  showRoleModal.value = true
}

async function handleRoleSave() {
  try {
    await assignUserRoles(roleUserId.value!, checkedRoleIds.value)
    message.success('角色分配成功')
    showRoleModal.value = false
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

onMounted(() => { load(); loadOpts() })
</script>

<template>
  <div>
    <!-- 搜索栏 -->
    <NForm v-show="showSearch" :model="queryParams" label-placement="left" label-width="80" style="margin-bottom: 12px">
      <NRow :gutter="16">
        <NCol :span="6"><NFormItem label="用户名"><NInput v-model:value="queryParams.username" placeholder="请输入用户名" clearable @keyup.enter="handleQuery" /></NFormItem></NCol>
        <NCol :span="6"><NFormItem label="手机号"><NInput v-model:value="queryParams.phone" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" /></NFormItem></NCol>
        <NCol :span="6"><NFormItem label="状态"><NSelect v-model:value="queryParams.status" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" placeholder="全部" clearable /></NFormItem></NCol>
        <NCol :span="6"><NSpace><NButton type="primary" @click="handleQuery">搜索</NButton><NButton @click="resetQuery">重置</NButton></NSpace></NCol>
      </NRow>
    </NForm>

    <!-- 工具栏 -->
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <NSpace>
        <NButton type="primary" @click="openCreate" v-has-permi="['user:create']">新增用户</NButton>
      </NSpace>
      <NButton text @click="showSearch = !showSearch">{{ showSearch ? '收起搜索' : '展开搜索' }}</NButton>
    </NSpace>

    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered />

    <!-- 新增/编辑 -->
    <NModal v-model:show="showModal" :key="String(isEdit) + String(editId)" preset="card" :title="isEdit ? '编辑用户' : '新增用户'" style="width: 520px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem label="用户名" required><NInput v-model:value="form.username" placeholder="请输入用户名" :disabled="isEdit" /></NFormItem>
        <NFormItem v-if="!isEdit" label="密码" required><NInput v-model:value="form.password" type="password" placeholder="请输入密码" /></NFormItem>
        <NFormItem label="昵称"><NInput v-model:value="form.nickname" placeholder="请输入昵称" /></NFormItem>
        <NFormItem label="邮箱"><NInput v-model:value="form.email" placeholder="请输入邮箱" /></NFormItem>
        <NFormItem label="手机号"><NInput v-model:value="form.phone" placeholder="请输入手机号" /></NFormItem>
        <NFormItem label="性别">
          <NRadioGroup v-model:value="form.sex"><NRadio value="0">男</NRadio><NRadio value="1">女</NRadio><NRadio value="2">未知</NRadio></NRadioGroup>
        </NFormItem>
        <NFormItem label="角色"><NSelect v-model:value="form.role" :options="roleOptions" /></NFormItem>
        <NFormItem label="部门"><NSelect v-model:value="form.departmentId" :options="deptOptions" placeholder="请选择部门" clearable /></NFormItem>
        <NFormItem label="状态"><NSelect v-model:value="form.status" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" /></NFormItem>
        <NSpace justify="end"><NButton @click="showModal = false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
      </NForm>
    </NModal>

    <!-- 重置密码 -->
    <NModal v-model:show="showPwdModal" preset="card" title="重置密码" style="width: 400px" :mask-closable="false">
      <NForm label-placement="left" label-width="80">
        <NFormItem label="用户"><span style="font-weight:600">{{ pwdUsername }}</span></NFormItem>
        <NFormItem label="新密码" required><NInput v-model:value="newPassword" type="password" placeholder="请输入新密码" /></NFormItem>
        <NSpace justify="end"><NButton @click="showPwdModal = false">取消</NButton><NButton type="primary" @click="handleResetPwd">确定</NButton></NSpace>
      </NForm>
    </NModal>

    <!-- 分配角色 -->
    <NModal v-model:show="showRoleModal" preset="card" :title="`分配角色 — ${roleUsername}`" style="width: 450px" :mask-closable="false">
      <NCheckboxGroup v-model:value="checkedRoleIds">
        <NSpace vertical>
          <NCheckbox v-for="r in allRoleOptions" :key="r.value" :value="r.value">{{ r.label }}</NCheckbox>
        </NSpace>
      </NCheckboxGroup>
      <NSpace justify="end" style="margin-top:16px">
        <NButton @click="showRoleModal = false">取消</NButton>
        <NButton type="primary" @click="handleRoleSave">确定</NButton>
      </NSpace>
    </NModal>
  </div>
</template>
