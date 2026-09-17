<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NTag, useMessage, NPopconfirm, NSelect } from 'naive-ui'
import { getParamPage, createParam, updateParam, deleteParam, getParamValues, saveParamValue, deleteParamValue } from '../../api/globalParam'
import { getEnvActive } from '../../api/environment'
import { formatDateTime } from '../../utils/format'
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
  { label: '参数名↑', value: 'param_name:ASC' },
  { label: '参数名↓', value: 'param_name:DESC' },
]

// 主 Modal
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ paramName: '', paramValue: '', remark: '' })

// 环境列表 Modal
const showEnvModal = ref(false)
const envParamName = ref('')
const envValues = ref<any[]>([])
const envLoading = ref(false)
const activeEnvs = ref<any[]>([])

// 环境值编辑 Modal
const showValueModal = ref(false)
const valueIsEdit = ref(false)
const currentEnvValue = ref({ paramValueId: null as number | null, paramId: null as number | null, envId: null as number | null, paramValue: '', envName: '' })

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '参数ID', key: 'paramId', width: 200 },
  { title: '参数名', key: 'paramName' },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  { title: '创建时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 280, render: (row: any) => h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', type: 'info', onClick: () => openEnvManager(row) }, { default: () => '参数管理' }),
        h(NButton, { size: 'small', tertiary: true, onClick: () => traceDrawer.value?.open('global_param', row.id, row.paramName) }, { default: () => '追踪' }),
        h(NButton, { size: 'small', onClick: () => openEditParam(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteParam(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确认删除？'
        })
      ]
    })
  },
]

async function load() {
  loading.value = true
  try {
    const res: any = await getParamPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }

// ====== 主参数操作 ======
function openCreate() {
  isEdit.value = false; editId.value = null
  form.value = { paramName: '', paramValue: '', remark: '' }
  showModal.value = true
}

function openEditParam(row: any) {
  isEdit.value = true; editId.value = row.id
  // 编辑时去掉 global_ 前缀展示
  const displayName = row.paramName.startsWith('global_') ? row.paramName.substring(7) : row.paramName
  form.value = { paramName: displayName, paramValue: '', remark: row.remark || '' }
  showModal.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) {
      await updateParam(editId.value!, { paramName: form.value.paramName, remark: form.value.remark })
    } else {
      await createParam({ paramName: form.value.paramName, paramValue: form.value.paramValue, remark: form.value.remark })
    }
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDeleteParam(id: number) { await deleteParam(id); message.success('删除成功'); load() }

// ====== 参数管理 ======
async function openEnvManager(row: any) {
  envParamName.value = row.paramName
  envLoading.value = true; showEnvModal.value = true
  try {
    const res: any = await getParamValues(row.paramName)
    envValues.value = (res && res.length > 0) ? (res[0].envValues || []) : []
    const envs: any = await getEnvActive()
    activeEnvs.value = envs || []
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '加载环境参数失败')
    showEnvModal.value = false
  } finally { envLoading.value = false }
}

function openAddValue() {
  valueIsEdit.value = false
  currentEnvValue.value = { paramValueId: null, paramId: getParamId(), envId: null, paramValue: '', envName: '' }
  showValueModal.value = true
}

function openEditValue(r: any) {
  valueIsEdit.value = true
  currentEnvValue.value = { paramValueId: r.paramValueId, paramId: getParamId(), envId: r.envId, paramValue: r.paramValue, envName: r.envName }
  showValueModal.value = true
}

async function handleSaveValue() {
  try {
    const v = currentEnvValue.value
    if (!v.envId) { message.error('请选择环境'); return }
    await saveParamValue(v.paramId!, v.envId, v.paramValue)
    message.success('保存成功'); showValueModal.value = false
    await openEnvManager({ paramName: envParamName.value })
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDeleteValue(envId: number) {
  await deleteParamValue(getParamId()!, envId)
  message.success('删除成功')
  await openEnvManager({ paramName: envParamName.value })
}

const availableEnvsForAdd = () => {
  const used = new Set(envValues.value.map((v: any) => v.envId))
  return activeEnvs.value.filter((e: any) => !used.has(e.id) && e.isDeleted !== 1).map((e: any) => ({ label: e.name, value: e.id }))
}

function getParamId(): number | null {
  const row = data.value.find((r: any) => r.paramName === envParamName.value)
  return row ? row.id : null
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>全局参数设置</h3>
      <NSpace>
        <NInput v-model:value="searchKeyword" placeholder="搜索参数名" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
        <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
        <NButton @click="page = 1; load()">搜索</NButton>
        <NButton type="primary" @click="openCreate">新增参数</NButton>
      </NSpace>
    </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered />

    <!-- 新增/编辑参数名 -->
    <NModal v-model:show="showModal" :key="String(isEdit) + String(editId)" preset="card" :title="isEdit ? '编辑参数' : '新增参数'" style="width: 520px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem label="参数名" required><NInput v-model:value="form.paramName" placeholder="请输入参数名，如 test" /></NFormItem>
        <NFormItem v-if="!isEdit" label="默认值"><NInput v-model:value="form.paramValue" type="textarea" placeholder="默认环境的参数值" /></NFormItem>
        <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" /></NFormItem>
        <NSpace justify="end"><NButton @click="showModal = false">取消</NButton><NButton type="primary" @click="handleSave">确定</NButton></NSpace>
      </NForm>
    </NModal>

    <!-- 参数管理：环境值列表 -->
    <NModal v-model:show="showEnvModal" preset="card" :title="`${envParamName} - 环境参数管理`" style="width: 740px" :mask-closable="false">
      <NSpace vertical>
        <NButton size="small" type="primary" @click="openAddValue">添加环境值</NButton>
        <NDataTable :columns="[
          { title: '环境', key: 'envName', width: 110 },
          { title: '参数值', key: 'paramValue', ellipsis: { tooltip: true } },
          { title: '状态', key: 'status', width: 90, render: (r: any) => r.status !== '正常' ? h(NTag, { type: 'warning', size: 'small' }, { default: () => r.status }) : h(NTag, { type: 'success', size: 'small' }, { default: () => '正常' }) },
          { title: '停用时间', key: 'disabledTime', width: 170, render: (r: any) => r.disabledTime ? formatDateTime(r.disabledTime) : '-' },
          { title: '操作', key: 'act', width: 140, render: (r: any) => h(NSpace, null, { default: () => [
            h(NButton, { size: 'tiny', onClick: () => openEditValue(r) }, { default: () => '编辑' }),
            h(NPopconfirm, { onPositiveClick: () => handleDeleteValue(r.envId) }, { trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { default: () => '删除' }), default: () => '确认删除？' })
          ]})}
        ]" :data="envValues" :loading="envLoading" size="small" bordered />
      </NSpace>
    </NModal>

    <!-- 编辑环境值 -->
    <NModal v-model:show="showValueModal" preset="card" :title="valueIsEdit ? `编辑环境值 - ${currentEnvValue.envName}` : '添加环境值'" style="width: 460px" :mask-closable="false">
      <NForm label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem v-if="!valueIsEdit" label="环境" required><NSelect v-model:value="currentEnvValue.envId" :options="availableEnvsForAdd()" placeholder="选择环境" /></NFormItem>
        <NFormItem label="参数值" required><NInput v-model:value="currentEnvValue.paramValue" type="textarea" placeholder="请输入该环境下的参数值" /></NFormItem>
        <NSpace justify="end"><NButton @click="showValueModal = false">取消</NButton><NButton type="primary" @click="handleSaveValue">确定</NButton></NSpace>
      </NForm>
    </NModal>

    <TraceDrawer ref="traceDrawer" />
  </div>
</template>
