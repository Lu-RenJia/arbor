<script setup lang="ts">
import { ref, watch, computed, h } from 'vue'
import { NButton, NSpace, NInput, NFormItem, NSwitch, NPopconfirm, NModal, NForm, NDataTable, useMessage } from 'naive-ui'
import { getFieldMappings, createFieldMapping, updateFieldMapping, deleteFieldMapping, batchSaveFieldMappings } from '../../api/fieldMapping'

const props = defineProps<{ flowId: number; sampleData?: any }>()
const show = defineModel<boolean>('show', { default: false })

const message = useMessage()
const list = ref<any[]>([])
const loading = ref(false)
const searchKey = ref('')
const showForm = ref(false)
const editing = ref<any>(null)
const form = ref({ fieldName: '', fieldLabel: '', mappingJson: '', sortOrder: 0, isEnabled: true, remark: '' })

// ====== 自动填充映射 ======
const showAutoFill = ref(false)
const autoFillRows = ref<any[]>([])

function extractKeys(data: any): string[] {
  if (!data) return []
  if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object') {
    return Object.keys(data[0])
  }
  if (typeof data === 'object') return Object.keys(data)
  return []
}

function openAutoFill() {
  const keys = extractKeys(props.sampleData)
  if (keys.length === 0) { message.warning('暂无测试结果数据'); return }
  // 合并已有映射
  const existingMap: Record<string, any> = {}
  for (const m of list.value) { existingMap[m.fieldName] = m }
  autoFillRows.value = keys.map((key, i) => {
    const exist = existingMap[key]
    return {
      fieldName: key,
      fieldLabel: exist ? exist.fieldLabel : key,
      mappingJson: exist ? (exist.mappingJson || '') : '',
      sortOrder: exist ? exist.sortOrder : i,
      isEnabled: exist ? exist.isEnabled !== 0 : true,
      remark: exist ? (exist.remark || '') : '',
    }
  })
  showAutoFill.value = true
}

async function handleAutoFillSave() {
  try {
    const payload = autoFillRows.value.map(r => ({
      fieldName: r.fieldName, fieldLabel: r.fieldLabel,
      mappingJson: r.mappingJson || null, sortOrder: r.sortOrder,
      isEnabled: r.isEnabled ? 1 : 0, remark: r.remark || null
    }))
    list.value = await batchSaveFieldMappings(props.flowId, payload) as any || []
    message.success('保存成功'); showAutoFill.value = false
  } catch { /* handled */ }
}

// ====== 基础CRUD ======
const filtered = computed(() => {
  if (!searchKey.value) return list.value
  const kw = searchKey.value.toLowerCase()
  return list.value.filter((r: any) => r.fieldName.toLowerCase().includes(kw) || r.fieldLabel.toLowerCase().includes(kw))
})

// immediate 必需：组件若带着 show=true 被新建（父级 v-if 翻转），无 false→true 跳变，非 immediate 的 watch 不会触发
watch(show, (v) => { if (v) load() }, { immediate: true })

async function load() {
  loading.value = true
  try { list.value = await getFieldMappings(props.flowId) as any || [] } catch { /* */ }
  finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  form.value = { fieldName: '', fieldLabel: '', mappingJson: '', sortOrder: 0, isEnabled: true, remark: '' }
  showForm.value = true
}

function openEdit(row: any) {
  editing.value = row
  form.value = {
    fieldName: row.fieldName, fieldLabel: row.fieldLabel,
    mappingJson: row.mappingJson || '', sortOrder: row.sortOrder || 0,
    isEnabled: row.isEnabled !== 0, remark: row.remark || ''
  }
  showForm.value = true
}

async function handleSave() {
  try {
    const payload = { ...form.value, isEnabled: form.value.isEnabled ? 1 : 0 }
    if (editing.value) { await updateFieldMapping(props.flowId, editing.value.id, payload); message.success('修改成功') }
    else { await createFieldMapping(props.flowId, payload); message.success('添加成功') }
    showForm.value = false; load()
  } catch { /* handled */ }
}

async function handleDelete(id: number) { await deleteFieldMapping(props.flowId, id); message.success('删除成功'); load() }

const autoFillColumns = [
  { title: '字段名', key: 'fieldName', width: 130 },
  { title: '中文名', key: 'fieldLabel', width: 120, render: (_: any, ri: number) => h(NInput, { value: autoFillRows.value[ri].fieldLabel, size:'tiny', onUpdateValue: (v: string) => autoFillRows.value[ri].fieldLabel = v, placeholder:'中文名' }) },
  { title: '字典映射', key: 'mappingJson', width: 180, render: (_: any, ri: number) => h(NInput, { value: autoFillRows.value[ri].mappingJson, size:'tiny', onUpdateValue: (v: string) => autoFillRows.value[ri].mappingJson = v, placeholder:'字典映射JSON' }) },
  { title: '排序', key: 'sortOrder', width: 60, render: (_: any, ri: number) => h(NInput, { value: String(autoFillRows.value[ri].sortOrder), size:'tiny', onUpdateValue: (v: string) => autoFillRows.value[ri].sortOrder = parseInt(v) || 0, style:'width:60px' }) },
  { title: '启用', key: 'isEnabled', width: 50, render: (_: any, ri: number) => h(NSwitch, { value: autoFillRows.value[ri].isEnabled, size:'small', onUpdateValue: (v: boolean) => autoFillRows.value[ri].isEnabled = v }) },
  { title: '备注', key: 'remark', width: 120, render: (_: any, ri: number) => h(NInput, { value: autoFillRows.value[ri].remark, size:'tiny', onUpdateValue: (v: string) => autoFillRows.value[ri].remark = v, placeholder:'备注' }) },
]

const columns = [
  { title: '#', key: '_idx', width: 40, render: (_: any, i: number) => i + 1 },
  { title: '字段名', key: 'fieldName', width: 130 },
  { title: '中文名', key: 'fieldLabel', width: 130 },
  { title: '字典映射', key: 'mappingJson', width: 140, ellipsis: { tooltip: true }, render: (row: any) => row.mappingJson || '—' },
  { title: '排序', key: 'sortOrder', width: 50 },
  { title: '启用', key: 'isEnabled', width: 50, render: (row: any) => row.isEnabled === 1 ? '✓' : '✗' },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  { title: '操作', key: 'actions', width: 120, render: (row: any) => h(NSpace, null, {
    default: () => [
      h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, { default: () => '编辑' }),
      h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除？'
      })
    ]
  })}
]
</script>

<template>
  <NModal v-model:show="show" preset="card" title="字段映射" style="width: 900px" :mask-closable="false">
    <NSpace vertical>
      <NSpace>
        <NButton type="primary" size="small" @click="openCreate">新增映射</NButton>
        <NButton size="small" type="info" @click="openAutoFill" :disabled="!sampleData">按照返回值映射</NButton>
        <NInput v-model:value="searchKey" placeholder="搜索字段名或中文名..." clearable style="width: 220px" size="small" />
      </NSpace>
      <NDataTable :columns="columns" :data="filtered" :loading="loading" size="small" bordered
        :max-height="400" :row-key="(r: any) => r.id" />
    </NSpace>
  </NModal>

  <!-- 新增/编辑 -->
  <NModal v-model:show="showForm" preset="card" :title="editing ? '编辑映射' : '新增映射'" style="width: 480px" :mask-closable="false">
    <NForm :model="form" label-placement="left" label-width="80">
      <NFormItem label="字段名" required>
        <NInput v-model:value="form.fieldName" placeholder="JSON key，如 status" :disabled="!!editing" />
      </NFormItem>
      <NFormItem label="中文名" required>
        <NInput v-model:value="form.fieldLabel" placeholder="展示列头，如 状态" />
      </NFormItem>
      <NFormItem label="字典映射">
        <NInput v-model:value="form.mappingJson" type="textarea" placeholder='{"1":"苹果","2":"橘子"}' :rows="3" />
      </NFormItem>
      <NFormItem label="排序">
        <NInput :value="String(form.sortOrder ?? '')" @update:value="(v: string) => form.sortOrder = parseInt(v) || 0" placeholder="数字越小越靠前" />
      </NFormItem>
      <NFormItem label="是否启用">
        <NSwitch v-model:value="form.isEnabled" />
      </NFormItem>
      <NFormItem label="备注">
        <NInput v-model:value="form.remark" placeholder="备注说明" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showForm = false">取消</NButton>
        <NButton type="primary" @click="handleSave">确定</NButton>
      </NSpace>
    </NForm>
  </NModal>

  <!-- 按照返回值批量填充映射 -->
  <NModal v-model:show="showAutoFill" preset="card" title="按照返回值映射" style="width: 800px" :mask-closable="false">
    <NSpace vertical>
      <NSpace>
        <NButton type="primary" size="small" @click="handleAutoFillSave">保存</NButton>
        <NButton size="small" @click="showAutoFill = false">取消</NButton>
      </NSpace>
      <NDataTable :columns="autoFillColumns" :data="autoFillRows" size="small" bordered :max-height="400"
        :row-key="(r: any) => r.fieldName" />
    </NSpace>
  </NModal>
</template>
