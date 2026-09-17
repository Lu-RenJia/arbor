<script setup lang="ts">
/**
 * 变量池维护弹窗（与旧设计器逻辑一致）：变量列表 + 新增/编辑/删除/批量删除。
 */
import { ref, h, watch } from 'vue'
import { NButton, NSpace, NInput, NFormItem, NSelect, NPopconfirm, NModal, NForm, NDataTable, useMessage } from 'naive-ui'
import { getFlowVariables, createFlowVariable, updateFlowVariable, deleteFlowVariable, deleteFlowVariablesBatch } from '../../../api/flowVariable'
import { getParamPage } from '../../../api/globalParam'
import { getDsPage } from '../../../api/datasource'
import type { DesignerNode } from './useFlowGraph'

const props = defineProps<{
  flowId: number
  /** 主线节点（含分支），用于"来源节点"选项 */
  mainNodes: DesignerNode[]
}>()

const show = defineModel<boolean>('show', { default: false })

const message = useMessage()

const varList = ref<any[]>([])
const varLoading = ref(false)
const showVarForm = ref(false)
const varEditing = ref<any>(null)
const varForm = ref<Record<string, any>>({ varName: '', paramId: null, nodeCode: null, nodeId: null, datasourceId: null })
const paramOptions = ref<any[]>([])
const dsOptions = ref<{ label: string; value: number }[]>([])
const selectedVars = ref<number[]>([])

// immediate 必需：组件若带着 show=true 被新建（父级 v-if 翻转），无 false→true 跳变，非 immediate 的 watch 不会触发
watch(show, (v) => { if (v) { loadParamOptions(); loadDsOptions(); loadVarList() } }, { immediate: true })

async function loadParamOptions() {
  try {
    const res: any = await getParamPage({ pageNum: 1, pageSize: 200 })
    paramOptions.value = (res.list || []).map((p: any) => ({
      label: `${p.paramName} [${p.paramValue}]`,
      value: p.id,
    }))
  } catch { /* ignore */ }
}

async function loadDsOptions() {
  try {
    const res: any = await getDsPage({ pageNum: 1, pageSize: 200 })
    dsOptions.value = (res.list || []).map((d: any) => ({
      label: `${d.name} (${d.dbType})`,
      value: d.id,
    }))
  } catch { /* ignore */ }
}

function nodeOptions() {
  return props.mainNodes.flatMap((n) => [n, ...n.branchNodes]).map((n) => ({
    label: `${n.nodeCode} (${n.nodeName})`,
    value: n.nodeCode,  // 使用 stable nodeCode，不再用 unstable DB id
  })).filter((o) => o.value != null)
}

async function loadVarList() {
  if (!props.flowId) return
  varLoading.value = true
  try {
    const res: any = await getFlowVariables(props.flowId)
    varList.value = res || []
  } finally { varLoading.value = false }
}

function openVarCreate() {
  varEditing.value = null
  varForm.value = { varName: '', paramId: null, nodeCode: null, nodeId: null, datasourceId: null }
  showVarForm.value = true
}

function openVarEdit(row: any) {
  varEditing.value = row
  varForm.value = { varName: row.varName, paramId: row.paramId, nodeCode: row.nodeCode, nodeId: row.nodeId, datasourceId: row.datasourceId }
  showVarForm.value = true
}

async function handleVarSave() {
  try {
    if (varEditing.value) {
      await updateFlowVariable(props.flowId, varEditing.value.id, varForm.value)
      message.success('修改成功')
    } else {
      await createFlowVariable(props.flowId, varForm.value)
      message.success('添加成功')
    }
    showVarForm.value = false
    loadVarList()
  } catch { /* 拦截器处理 */ }
}

async function handleVarDelete(id: number) {
  await deleteFlowVariable(props.flowId, id)
  message.success('删除成功')
  loadVarList()
}

async function handleVarDeleteBatch() {
  if (selectedVars.value.length === 0) { message.warning('请勾选变量'); return }
  await deleteFlowVariablesBatch(props.flowId, selectedVars.value)
  message.success('删除成功')
  selectedVars.value = []
  loadVarList()
}

const varColumns = [
  { type: 'selection' as any },
  { title: '变量名', key: 'varName' },
  { title: '全局变量值', key: 'paramValue' },
  { title: '全局变量名', key: 'paramName' },
  { title: '备注', key: 'paramRemark' },
  { title: '来源节点', key: 'nodeCode' },
  { title: '数据源', key: 'datasourceName' },
  { title: '操作', key: 'actions', width: 100, render: (row: any) => h(NSpace, null, {
    default: () => [
      h(NButton, { size: 'tiny', onClick: () => openVarEdit(row) }, { default: () => '编辑' }),
      h(NPopconfirm, { onPositiveClick: () => handleVarDelete(row.id) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除？',
      }),
    ],
  })},
]
</script>

<template>
  <NModal v-model:show="show" preset="card" title="变量池维护" style="width: 700px" :mask-closable="false">
    <NSpace vertical>
      <NSpace>
        <NButton type="primary" size="small" @click="openVarCreate">新增变量</NButton>
        <NButton size="small" @click="handleVarDeleteBatch" :disabled="selectedVars.length === 0">批量删除</NButton>
      </NSpace>
      <NDataTable
        :columns="varColumns"
        :data="varList"
        :loading="varLoading"
        :row-key="(row: any) => row.id"
        v-model:checked-row-keys="selectedVars"
        size="small"
        bordered
      />
    </NSpace>
  </NModal>

  <!-- 变量池新增/编辑 Dialog -->
  <NModal v-model:show="showVarForm" preset="card" :title="varEditing ? '编辑变量' : '新增变量'" style="width: 420px" :mask-closable="false">
    <NForm :model="varForm" label-placement="left" label-width="80">
      <NFormItem label="变量名" required>
        <NInput v-model:value="varForm.varName" placeholder="脚本中使用的名字，如 host、port" />
      </NFormItem>
      <NFormItem label="全局参数">
        <NSelect v-model:value="varForm.paramId" :options="paramOptions" placeholder="选择全局参数" filterable clearable @update:value="() => { if (varForm.paramId) { varForm.nodeCode = null; varForm.nodeId = null; varForm.datasourceId = null } }" />
      </NFormItem>
      <NFormItem label="节点">
        <NSelect v-model:value="varForm.nodeCode" :options="nodeOptions()" placeholder="选择节点（以节点编码关联，删除重建不丢失绑定）" clearable @update:value="() => { if (varForm.nodeCode) { varForm.paramId = null; varForm.datasourceId = null } }" />
      </NFormItem>
      <NFormItem label="数据源">
        <NSelect v-model:value="varForm.datasourceId" :options="dsOptions" placeholder="选择数据源（用于Groovy执行SQL）" clearable @update:value="() => { if (varForm.datasourceId) { varForm.paramId = null; varForm.nodeCode = null; varForm.nodeId = null } }" />
      </NFormItem>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="showVarForm = false">取消</NButton>
        <NButton type="primary" @click="handleVarSave">确定</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
