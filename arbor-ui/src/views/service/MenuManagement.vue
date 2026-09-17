<script setup lang="ts">
import { ref, onMounted, h, nextTick, computed } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NTag, useMessage, NPopconfirm, NTreeSelect, NInputNumber, NPopover, NRadioGroup, NRadio } from 'naive-ui'
import { getMenuList, createMenu, updateMenu, deleteMenu } from '../../api/menu'
import { getPermissionList } from '../../api/permission'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const menuList = ref<any[]>([])
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ parentId: 0 as number | null, name: '', menuType: 'M', path: '', component: '', query: '', icon: '', permissionCode: '', sortOrder: 0, visible: 1, status: '0', isFrame: '0', isCache: '0', routeName: '' })
const permOptions = ref<{ label: string; value: string }[]>([])
const treeSelectOptions = ref<any[]>([])
const refreshTable = ref(true)
const isExpandAll = ref(true)
const showIconPicker = ref(false)

// 图标列表
const iconList = ['DashboardOutlined','HomeOutlined','SettingOutlined','UserOutlined','TeamOutlined','SafetyOutlined','MenuOutlined',
  'ApartmentOutlined','FileTextOutlined','DatabaseOutlined','CodeOutlined','ApiOutlined','CloudServerOutlined','AppstoreOutlined',
  'RocketOutlined','EnvironmentOutlined','SendOutlined','EditOutlined','DeleteOutlined','SearchOutlined','PlusOutlined',
  'DownloadOutlined','UploadOutlined','EyeOutlined','LockOutlined','UnlockOutlined','KeyOutlined','BellOutlined','MessageOutlined',
  'MailOutlined','PhoneOutlined','CalendarOutlined','ClockCircleOutlined','InfoCircleOutlined','CheckCircleOutlined',
  'CloseCircleOutlined','ExclamationCircleOutlined','StarOutlined','HeartOutlined','TagOutlined','LinkOutlined']
const iconSearch = ref('')
const filteredIcons = computed(() => iconSearch.value ? iconList.filter(i => i.toLowerCase().includes(iconSearch.value.toLowerCase())) : iconList)

const menuTypeOptions = [
  { label: '目录', value: 'M' }, { label: '菜单', value: 'C' }, { label: '按钮', value: 'F' }
]

// 客户端建树：按 parentId 组装为嵌套结构
function handleTree(arr: any[], idField = 'id', parentField = 'parentId'): any[] {
  const map = new Map<any, any>(); const roots: any[] = []
  for (const d of arr) { d.children = []; map.set(d[idField], d) }
  for (const d of arr) {
    const parent = map.get(d[parentField])
    if (parent && parent[idField] !== d[idField]) { parent.children.push(d) }
    else { roots.push(d) }
  }
  return roots
}

function flattenForSelect(nodes: any[]): any[] {
  return nodes.filter(n => n.menuType !== 'F').map(n => ({
    label: n.name, value: n.id, children: n.children ? flattenForSelect(n.children) : []
  }))
}

const columns = [
  { title: '菜单名称', key: 'name', width: 200 },
  { title: '图标', key: 'icon', width: 70, render: (row: any) => row.icon || '-' },
  { title: '排序', key: 'sortOrder', width: 60 },
  { title: '权限标识', key: 'permissionCode', ellipsis: { tooltip: true }, width: 160 },
  { title: '路由', key: 'path', width: 140 },
  { title: '组件', key: 'component', width: 160, render: (row: any) => row.component || '-' },
  { title: '状态', key: 'status', width: 70, render: (row: any) => row.status !== '1' ? h(NTag, { type: 'success', size: 'small' }, { default: () => '正常' }) : h(NTag, { type: 'error', size: 'small' }, { default: () => '停用' }) },
  { title: '创建时间', key: 'createTime', width: 160, render: (row: any) => formatDateTime(row.createTime) },
  { title: '操作', key: 'actions', width: 220, render: (row: any) => h(NSpace, null, { default: () => [
    h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '修改' }),
    h(NButton, { size: 'small', type: 'primary', onClick: () => openCreate(row) }, { default: () => '新增' }),
    h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
      trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
      default: () => '确认删除该菜单？'
    })
  ]})},
]

async function load() {
  try {
    const res: any = await getMenuList()
    menuList.value = handleTree(res || [])
  } catch {/* ignore */}
}

async function loadTreeSelect() {
  const res: any = await getMenuList()
  treeSelectOptions.value = flattenForSelect(handleTree(res || []))
}

async function loadPerms() {
  const res: any = await getPermissionList()
  permOptions.value = (res || []).map((p: any) => ({ label: `${p.code} - ${p.name}`, value: p.code }))
}

function toggleExpandAll() {
  refreshTable.value = false; isExpandAll.value = !isExpandAll.value
  nextTick(() => { refreshTable.value = true })
}

function openCreate(row?: any) {
  isEdit.value = false; editId.value = null
  form.value = { parentId: row ? row.id : 0, name: '', menuType: 'M', path: '', component: '', query: '', icon: '', permissionCode: '', sortOrder: 0, visible: 1, status: '0', isFrame: '0', isCache: '0', routeName: '' }
  loadTreeSelect(); loadPerms(); showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { parentId: row.parentId || 0, name: row.name, menuType: row.menuType || 'M', path: row.path || '', component: row.component || '', query: row.query || '', icon: row.icon || '', permissionCode: row.permissionCode || '', sortOrder: row.sortOrder || 0, visible: row.visible ?? 1, status: row.status || '0', isFrame: row.isFrame || '0', isCache: row.isCache || '0', routeName: row.routeName || '' }
  loadTreeSelect(); loadPerms(); showModal.value = true
}

function selectIcon(name: string) { form.value.icon = name; showIconPicker.value = false }

async function handleSave() {
  try {
    const payload: any = { ...form.value, parentId: form.value.parentId || 0 }
    if (isEdit.value) await updateMenu(editId.value!, payload)
    else await createMenu(payload)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDelete(id: number) {
  try { await deleteMenu(id); message.success('删除成功'); load() }
  catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom:12px">
      <NSpace>
        <NButton type="primary" @click="openCreate()">新增</NButton>
        <NButton @click="toggleExpandAll">{{ isExpandAll ? '折叠全部' : '展开全部' }}</NButton>
      </NSpace>
    </NSpace>

    <NDataTable v-if="refreshTable" :columns="columns" :data="menuList" :row-key="(row: any) => row.id" children-key="children" :default-expand-all="isExpandAll" bordered />

    <NModal v-model:show="showModal" :key="String(isEdit)+String(editId)" preset="card" :title="isEdit?'编辑菜单':'新增菜单'" style="width:640px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="100">
        <NFormItem label="上级菜单"><NTreeSelect v-model:value="form.parentId" :options="treeSelectOptions" placeholder="无（顶级菜单）" clearable /></NFormItem>
        <NFormItem label="菜单类型" required>
          <NRadioGroup v-model:value="form.menuType">
            <NRadio v-for="o in menuTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadio>
          </NRadioGroup>
        </NFormItem>
        <NFormItem label="菜单名称" required><NInput v-model:value="form.name" placeholder="请输入菜单名称" /></NFormItem>
        <NFormItem label="路由地址" v-if="form.menuType !== 'F'"><NInput v-model:value="form.path" placeholder="如：system/user" /></NFormItem>
        <NFormItem label="组件路径" v-if="form.menuType === 'C'"><NInput v-model:value="form.component" placeholder="如：system/user/index" /></NFormItem>
        <NFormItem label="路由参数" v-if="form.menuType === 'C'"><NInput v-model:value="form.query" placeholder="如：id=1" /></NFormItem>
        <NFormItem label="权限标识" v-if="form.menuType !== 'M'"><NInput v-model:value="form.permissionCode" placeholder="如：system:user:list" /></NFormItem>
        <NFormItem label="图标" v-if="form.menuType !== 'F'">
          <NSpace>
            <NInput v-model:value="form.icon" placeholder="点击选择图标" style="width:180px" readonly />
            <NPopover trigger="click" :show="showIconPicker" @update:show="showIconPicker = $event">
              <template #trigger><NButton @click="showIconPicker = !showIconPicker">{{ form.icon ? '更换图标' : '选择图标' }}</NButton></template>
              <div style="width:340px;padding:8px">
                <NInput v-model:value="iconSearch" placeholder="搜索图标..." size="small" style="margin-bottom:8px" />
                <div style="max-height:300px;overflow-y:auto;display:flex;flex-wrap:wrap;gap:4px">
                  <NButton v-for="ic in filteredIcons" :key="ic" size="tiny" :type="form.icon===ic?'primary':'default'" @click="selectIcon(ic)" style="font-size:11px;min-width:90px">{{ ic }}</NButton>
                </div>
              </div>
            </NPopover>
            <NButton v-if="form.icon" size="small" @click="form.icon = ''">清除</NButton>
          </NSpace>
        </NFormItem>
        <NFormItem label="是否外链" v-if="form.menuType !== 'F'">
          <NRadioGroup v-model:value="form.isFrame"><NRadio value="0">否</NRadio><NRadio value="1">是</NRadio></NRadioGroup>
        </NFormItem>
        <NFormItem label="是否缓存" v-if="form.menuType === 'C'">
          <NRadioGroup v-model:value="form.isCache"><NRadio value="0">否</NRadio><NRadio value="1">是</NRadio></NRadioGroup>
        </NFormItem>
        <NFormItem label="显示状态" v-if="form.menuType !== 'F'">
          <NRadioGroup v-model:value="form.visible"><NRadio :value="1">显示</NRadio><NRadio :value="0">隐藏</NRadio></NRadioGroup>
        </NFormItem>
        <NFormItem label="路由名称" v-if="form.menuType === 'C'"><NInput v-model:value="form.routeName" placeholder="Vue Router name" /></NFormItem>
        <NFormItem label="排序"><NInputNumber v-model:value="form.sortOrder" :min="0" style="width:100%" /></NFormItem>
        <NFormItem label="状态">
          <NRadioGroup v-model:value="form.status"><NRadio value="0">正常</NRadio><NRadio value="1">停用</NRadio></NRadioGroup>
        </NFormItem>
        <NSpace justify="end"><NButton @click="showModal=false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
      </NForm>
    </NModal>
  </div>
</template>
