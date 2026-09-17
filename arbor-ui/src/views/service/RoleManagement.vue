<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NCheckbox, useMessage, NPopconfirm, NTag, NSelect, NRow, NCol, NTree } from 'naive-ui'
import { getRolePage, createRole, updateRole, deleteRole, getRoleDetail, assignPermissions, saveDataScope } from '../../api/role'
import { getPermissionList } from '../../api/permission'
import { getMenuTree } from '../../api/menu'
import { getDeptTree } from '../../api/department'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const showSearch = ref(true)
const queryParams = ref({ name: '', roleKey: '', status: '' })
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ code: '', name: '', roleKey: '', remark: '', roleSort: 0, status: '0' })

// ====== 权限分配（菜单树模式） ======
const showPermModal = ref(false)
const permRoleId = ref<number | null>(null)
const permRoleName = ref('')
const checkedPermIds = ref<Set<number>>(new Set())

// 数据权限
const showDataScopeModal = ref(false)
const dataScopeRoleId = ref<number | null>(null)
const dataScopeRoleName = ref('')
const dataScopeForm = ref({ dataScope: '1', deptIds: [] as number[] })
const deptTreeData = ref<any[]>([])
const permCodeToId = ref<Map<string, number>>(new Map())   // permission_code → permission.id
const menuTree = ref<any[]>([])

interface FlatNode {
  menuId: number
  name: string
  depth: number
  permissionCode: string | null
  permissionId: number | null
  isDir: boolean       // 目录（无权限码、有子节点）
  isPage: boolean      // 页面（有权限码、有path）
  isButton: boolean    // 按钮（有权限码、无path）
  children: any[]
}

// 菜单树扁平化（含按钮条目）
const flatPermTree = computed<FlatNode[]>(() => {
  const result: FlatNode[] = []
  function walk(items: any[], depth: number) {
    for (const item of items) {
      const hasPath = !!item.path
      const hasPermCode = !!item.permissionCode
      const hasChildren = item.children && item.children.length > 0
      result.push({
        menuId: item.id,
        name: item.name,
        depth,
        permissionCode: item.permissionCode || null,
        permissionId: hasPermCode ? (permCodeToId.value.get(item.permissionCode) ?? null) : null,
        isDir: !hasPermCode && hasChildren,
        isPage: hasPermCode && hasPath,
        isButton: hasPermCode && !hasPath,
        children: item.children || [],
      })
      if (item.children) walk(item.children, depth + 1)
    }
  }
  walk(menuTree.value, 0)
  return result
})

// 收集某节点下所有子孙权限ID
function collectDescendantPermIds(node: FlatNode): number[] {
  const ids: number[] = []
  if (node.permissionId != null) ids.push(node.permissionId)
  function walkChildren(children: any[]) {
    for (const child of children) {
      if (child.permissionCode) {
        const pid = permCodeToId.value.get(child.permissionCode)
        if (pid != null) ids.push(pid)
      }
      if (child.children) walkChildren(child.children)
    }
  }
  walkChildren(node.children)
  return ids
}

// 目录节点：是否全选
function isDirAllChecked(node: FlatNode): boolean {
  const descendants = collectDescendantPermIds(node)
  if (descendants.length === 0) return false
  return descendants.every(id => checkedPermIds.value.has(id))
}

// 目录节点：是否半选
function isDirIndeterminate(node: FlatNode): boolean {
  const descendants = collectDescendantPermIds(node)
  if (descendants.length === 0) return false
  const some = descendants.some(id => checkedPermIds.value.has(id))
  const all = descendants.every(id => checkedPermIds.value.has(id))
  return some && !all
}

// 目录勾选 → 级联子节点
function toggleDir(node: FlatNode, val: boolean) {
  const descendants = collectDescendantPermIds(node)
  if (val) {
    descendants.forEach(id => checkedPermIds.value.add(id))
  } else {
    descendants.forEach(id => checkedPermIds.value.delete(id))
  }
  // 触发响应式更新
  checkedPermIds.value = new Set(checkedPermIds.value)
}

// 单个权限勾选
function togglePerm(permId: number, val: boolean) {
  if (val) checkedPermIds.value.add(permId)
  else checkedPermIds.value.delete(permId)
  checkedPermIds.value = new Set(checkedPermIds.value)
}

// ====== 列 / CRUD ======
const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '角色编码', key: 'code' },
  { title: '角色名称', key: 'name' },
  { title: '权限标识', key: 'roleKey' },
  { title: '排序', key: 'roleSort', width: 70 },
  { title: '状态', key: 'status', width: 80, render: (row: any) => row.status === '0' ? h(NTag, { type: 'success', size: 'small' }, { default: () => '正常' }) : h(NTag, { type: 'error', size: 'small' }, { default: () => '停用' }) },
  { title: '创建时间', key: 'createTime', width: 170, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 170, render: (row: any) => formatDateTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 220, render: (row: any) => {
      const btns: any[] = []
      if (row.code !== 'ADMIN' || auth.isAdmin) {
        btns.push(h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }))
        btns.push(h(NButton, { size: 'small', type: 'info', onClick: () => openPermissions(row) }, { default: () => '权限' }))
        btns.push(h(NButton, { size: 'small', type: 'warning', onClick: () => openDataScope(row) }, { default: () => '数据权限' }))
      }
      if (row.code !== 'ADMIN' && row.code !== 'USER') {
        btns.push(
          h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => '确认删除该角色？'
          })
        )
      }
      return h(NSpace, null, { default: () => btns })
    }
  },
]

async function load() {
  loading.value = true
  try {
    const res: any = await getRolePage({ ...queryParams.value, pageNum: page.value, pageSize: pageSize.value })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}
function handleQuery() { page.value = 1; load() }
function resetQuery() { queryParams.value = { name: '', roleKey: '', status: '' }; handleQuery() }

function openCreate() {
  isEdit.value = false; editId.value = null
  form.value = { code: '', name: '', roleKey: '', remark: '', roleSort: 0, status: '0' }
  showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { code: row.code, name: row.name, roleKey: row.roleKey || row.code, remark: row.remark || '', roleSort: row.roleSort || 0, status: row.status || '0' }
  showModal.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) await updateRole(editId.value!, form.value)
    else await createRole(form.value)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDelete(id: number) {
  await deleteRole(id); message.success('删除成功'); load()
}

async function openPermissions(row: any) {
  permRoleId.value = row.id
  permRoleName.value = row.name
  // 并行加载：菜单树 + 全部权限 + 该角色已有权限ID
  const [menu, perms, detail]: [any, any, any] = await Promise.all([
    getMenuTree(), getPermissionList(), getRoleDetail(row.id)
  ])
  // 构建 permission_code → id 映射
  const map = new Map<string, number>()
  for (const p of (perms || [])) {
    map.set(p.code, p.id)
  }
  permCodeToId.value = map
  menuTree.value = menu || []
  checkedPermIds.value = new Set(detail.permissionIds || [])
  showPermModal.value = true
}

async function handlePermSave() {
  if (permRoleId.value == null) return
  try {
    await assignPermissions(permRoleId.value, Array.from(checkedPermIds.value))
    message.success('权限分配成功')
    showPermModal.value = false
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function openDataScope(row: any) {
  dataScopeRoleId.value = row.id; dataScopeRoleName.value = row.name
  dataScopeForm.value = { dataScope: row.dataScope || '1', deptIds: [] }
  try {
    const depts: any = await getDeptTree()
    deptTreeData.value = depts || []
  } catch { deptTreeData.value = [] }
  showDataScopeModal.value = true
}

async function handleDataScopeSave() {
  try {
    await saveDataScope(dataScopeRoleId.value!, dataScopeForm.value.dataScope)
    message.success('数据权限分配成功')
    showDataScopeModal.value = false
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

onMounted(load)
</script>

<template>
  <div>
    <!-- 搜索栏 -->
    <NForm v-show="showSearch" :model="queryParams" label-placement="left" label-width="80" style="margin-bottom:12px">
      <NRow :gutter="16">
        <NCol :span="6"><NFormItem label="角色名称"><NInput v-model:value="queryParams.name" placeholder="请输入" clearable /></NFormItem></NCol>
        <NCol :span="6"><NFormItem label="权限标识"><NInput v-model:value="queryParams.roleKey" placeholder="请输入" clearable /></NFormItem></NCol>
        <NCol :span="6"><NFormItem label="状态"><NSelect v-model:value="queryParams.status" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" placeholder="全部" clearable /></NFormItem></NCol>
        <NCol :span="6"><NSpace><NButton type="primary" @click="handleQuery">搜索</NButton><NButton @click="resetQuery">重置</NButton></NSpace></NCol>
      </NRow>
    </NForm>

    <NSpace justify="space-between" style="margin-bottom: 12px">
      <NSpace>
        <NButton type="primary" @click="openCreate">新增角色</NButton>
      </NSpace>
      <NButton text @click="showSearch = !showSearch">{{ showSearch ? '收起搜索' : '展开搜索' }}</NButton>
    </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading"
      :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered>
    </NDataTable>

    <!-- 角色 CRUD 弹窗 -->
    <NModal v-model:show="showModal" :key="String(isEdit) + String(editId)" preset="card"
      :title="isEdit ? '编辑角色' : '新增角色'" style="width: 500px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem label="角色编码" required>
          <NInput v-model:value="form.code" placeholder="如：OPERATOR" :disabled="isEdit && editId === 1" />
        </NFormItem>
        <NFormItem label="角色名称" required>
          <NInput v-model:value="form.name" placeholder="如：运维人员" />
        </NFormItem>
        <NFormItem label="权限标识">
          <NInput v-model:value="form.roleKey" placeholder="如：operator" />
        </NFormItem>
        <NFormItem label="排序">
          <NInput :value="String(form.roleSort)" placeholder="数字越小越靠前" @update:value="(v:string) => form.roleSort = Number(v) || 0" />
        </NFormItem>
        <NFormItem label="状态">
          <NSelect v-model:value="form.status" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" placeholder="请输入备注" type="textarea" />
        </NFormItem>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="handleSave">确定</NButton>
        </NSpace>
      </NForm>
    </NModal>

    <!-- 权限分配弹窗 — 菜单树 -->
    <NModal v-model:show="showPermModal" preset="card"
      :title="`权限分配 — ${permRoleName}`" style="width: 720px" :mask-closable="false">
      <div style="max-height: 55vh; overflow-y: auto">
        <div v-for="node in flatPermTree" :key="node.menuId"
          :style="{ paddingLeft: node.depth * 24 + 'px', marginBottom: node.isDir ? '6px' : '2px' }">
          <!-- 目录节点 -->
          <NCheckbox v-if="node.isDir"
            :checked="isDirAllChecked(node)"
            :indeterminate="isDirIndeterminate(node)"
            @update:checked="(val: boolean) => toggleDir(node, val)">
            <span style="font-weight: 600">{{ node.name }}</span>
          </NCheckbox>
          <!-- 页面节点 -->
          <NCheckbox v-else-if="node.isPage"
            :checked="node.permissionId != null && checkedPermIds.has(node.permissionId)"
            @update:checked="(val: boolean) => node.permissionId != null && togglePerm(node.permissionId, val)">
            {{ node.name }}
            <NTag size="tiny" type="info" style="margin-left: 6px; vertical-align: middle">{{ node.permissionCode }}</NTag>
          </NCheckbox>
          <!-- 按钮节点 -->
          <NCheckbox v-else-if="node.isButton"
            :checked="node.permissionId != null && checkedPermIds.has(node.permissionId)"
            @update:checked="(val: boolean) => node.permissionId != null && togglePerm(node.permissionId, val)">
            {{ node.name }}
            <NTag size="tiny" type="warning" style="margin-left: 4px; vertical-align: middle">操作</NTag>
            <span style="color: #999; font-size: 12px; margin-left: 4px">{{ node.permissionCode }}</span>
          </NCheckbox>
        </div>
        <div v-if="flatPermTree.length === 0" style="text-align: center; color: #999; padding: 24px">
          暂无菜单数据，请先配置菜单
        </div>
      </div>
      <NSpace justify="end" style="margin-top: 12px">
        <NButton @click="showPermModal = false">取消</NButton>
        <NButton type="primary" @click="handlePermSave">保存权限</NButton>
      </NSpace>
    </NModal>

    <!-- 数据权限 -->
    <NModal v-model:show="showDataScopeModal" preset="card" :title="`数据权限 — ${dataScopeRoleName}`" style="width:500px" :mask-closable="false">
      <NForm :model="dataScopeForm" label-placement="left" label-width="100">
        <NFormItem label="权限范围">
          <NSelect v-model:value="dataScopeForm.dataScope" :options="[
            { label: '全部数据权限', value: '1' },
            { label: '自定义数据权限', value: '2' },
            { label: '本部门数据权限', value: '3' },
            { label: '本部门及以下数据权限', value: '4' },
            { label: '仅本人数据权限', value: '5' },
          ]" />
        </NFormItem>
        <NFormItem v-if="dataScopeForm.dataScope === '2'" label="选择部门">
          <NTree :data="deptTreeData" checkable :default-expand-all="true" label-field="name" key-field="id" children-field="children" style="max-height:240px;overflow-y:auto;border:1px solid #eee;border-radius:4px;padding:8px" />
        </NFormItem>
      </NForm>
      <NSpace justify="end" style="margin-top:12px">
        <NButton @click="showDataScopeModal = false">取消</NButton>
        <NButton type="primary" @click="handleDataScopeSave">确定</NButton>
      </NSpace>
    </NModal>
  </div>
</template>
