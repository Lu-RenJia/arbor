<script setup lang="ts">
/**
 * 参数表单填写弹窗（使用应用时按配置的 schema 录入参数）
 * - 对象：直接渲染表单
 * - 列表：渲染数据表格 + 新增/编辑子弹窗，可增删改
 * - 字段按 fieldType 渲染（文本/数字/布尔），必填校验，确认后生成 JSON 字符串
 */
import { ref, computed, watch, h } from 'vue'
import { NModal, NForm, NFormItem, NInput, NInputNumber, NSwitch, NButton, NSpace, NDataTable, useMessage } from 'naive-ui'
import type { ParamSchema, ParamSchemaField } from '../flow/flow-designer-vueflow/useFlowGraph'

const show = defineModel<boolean>('show', { default: false })
const props = defineProps<{ schema: ParamSchema | null }>()
const emit = defineEmits<{ (e: 'confirm', json: string): void }>()

const message = useMessage()

const isList = computed(() => props.schema?.type === 'list')
const fields = computed<ParamSchemaField[]>(() => props.schema?.fields || [])

/** 对象模式的表单值 */
const formValues = ref<Record<string, any>>({})
/** 列表模式的数据行 */
const listRows = ref<Record<string, any>[]>([])
/** 列表行新增/编辑子弹窗 */
const showRowModal = ref(false)
const editingRowIndex = ref<number | null>(null)
const rowFormValues = ref<Record<string, any>>({})
let ridSeq = 0

function defaultValue(f: ParamSchemaField): any {
  if (f.fieldType === 'number') return null
  if (f.fieldType === 'boolean') return false
  return ''
}

function initForm(): Record<string, any> {
  const v: Record<string, any> = {}
  for (const f of fields.value) v[f.name] = defaultValue(f)
  return v
}

// immediate 必需：组件若带着 show=true 被新建（父级 v-if 翻转），无 false→true 跳变，
// 非 immediate 的 watch 不会触发，表单初始化会被静默跳过
watch(show, (v) => {
  if (!v) return
  if (isList.value) {
    listRows.value = []
  } else {
    formValues.value = initForm()
  }
}, { immediate: true })

/** 必填校验（返回错误提示，null 表示通过） */
function validateRow(row: Record<string, any>): string | null {
  for (const f of fields.value) {
    const val = row[f.name]
    if (f.required && (val === null || val === undefined || String(val).trim() === '')) {
      return `「${f.label || f.name}」为必填项`
    }
  }
  return null
}

/** 按字段类型规整值，生成干净的输出对象（自动忽略 _rid 等内部字段） */
function buildValue(row: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {}
  for (const f of fields.value) {
    const val = row[f.name]
    if (f.fieldType === 'number') {
      out[f.name] = (val === null || val === undefined || val === '') ? null : Number(val)
    } else if (f.fieldType === 'boolean') {
      out[f.name] = !!val
    } else {
      out[f.name] = val === null || val === undefined ? '' : String(val)
    }
  }
  return out
}

// ===== 对象模式 =====
function handleConfirmObject() {
  const err = validateRow(formValues.value)
  if (err) { message.warning(err); return }
  emit('confirm', JSON.stringify(buildValue(formValues.value), null, 2))
  show.value = false
}

// ===== 列表模式 =====
const columns = computed<any[]>(() => [
  ...fields.value.map(f => ({
    title: f.label || f.name,
    key: f.name,
    ellipsis: { tooltip: true },
    render: (row: any) => {
      const v = row[f.name]
      if (f.fieldType === 'boolean') return v ? '是' : '否'
      return v === null || v === undefined ? '' : String(v)
    },
  })),
  {
    title: '操作', key: '_action', width: 120, fixed: 'right',
    render: (_row: any, index: number) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'tiny', onClick: () => openEditRow(index) }, { default: () => '编辑' }),
        h(NButton, { size: 'tiny', type: 'error', ghost: true, onClick: () => removeRow(index) }, { default: () => '删除' }),
      ],
    }),
  },
])

function openAddRow() {
  editingRowIndex.value = null
  rowFormValues.value = initForm()
  showRowModal.value = true
}
function openEditRow(index: number) {
  editingRowIndex.value = index
  rowFormValues.value = { ...listRows.value[index] }
  showRowModal.value = true
}
function handleRowConfirm() {
  const err = validateRow(rowFormValues.value)
  if (err) { message.warning(err); return }
  const row = buildValue(rowFormValues.value)
  if (editingRowIndex.value === null) {
    listRows.value.push({ ...row, _rid: ++ridSeq })
  } else {
    listRows.value[editingRowIndex.value] = { ...row, _rid: listRows.value[editingRowIndex.value]._rid }
  }
  showRowModal.value = false
}
function removeRow(index: number) { listRows.value.splice(index, 1) }

function handleConfirmList() {
  if (!listRows.value.length) { message.warning('请至少添加一行数据'); return }
  emit('confirm', JSON.stringify(listRows.value.map(r => buildValue(r)), null, 2))
  show.value = false
}

/** 统一确认入口（#footer 不能嵌套在 v-if/v-else 内，按形态分发） */
function handleConfirm() {
  if (isList.value) handleConfirmList()
  else handleConfirmObject()
}
</script>

<template>
  <NModal v-model:show="show" preset="card" :title="isList ? '📋 填写参数（列表）' : '📋 填写参数（对象）'" style="width: 720px" :mask-closable="false">
    <!-- 对象模式：直接表单 -->
    <NForm v-if="!isList" label-placement="top" style="max-height: 460px; overflow-y: auto">
      <NFormItem v-for="f in fields" :key="f.name" :label="(f.label || f.name) + (f.required ? ' *' : '')">
        <NInput v-if="f.fieldType === 'string'" v-model:value="formValues[f.name]" :placeholder="f.remark || ('请输入 ' + (f.label || f.name))" />
        <NInputNumber v-else-if="f.fieldType === 'number'" v-model:value="formValues[f.name]" :placeholder="f.remark || ('请输入 ' + (f.label || f.name))" style="width: 100%" :show-button="false" />
        <NSwitch v-else v-model:value="formValues[f.name]" />
        <template v-if="f.remark && f.fieldType !== 'string'">
          <div style="color:#999;font-size:11px">{{ f.remark }}</div>
        </template>
      </NFormItem>
    </NForm>

    <!-- 列表模式：数据表格 + 新增 -->
    <template v-else>
      <NSpace justify="space-between" style="width:100%; margin-bottom: 10px">
        <span style="font-size:12px;color:#999">共 {{ listRows.length }} 行</span>
        <NButton size="small" type="primary" @click="openAddRow">＋ 新增一行</NButton>
      </NSpace>
      <NDataTable :columns="columns" :data="listRows" :row-key="(row: any) => row._rid" :max-height="380" size="small" :bordered="true" />
    </template>

    <!-- 具名插槽必须是 NModal 的直接子节点，不能嵌套在 v-if/v-else 内 -->
    <template #footer>
      <NSpace justify="end" style="width:100%">
        <NButton size="small" @click="show = false">取消</NButton>
        <NButton size="small" type="primary" @click="handleConfirm">确定</NButton>
      </NSpace>
    </template>
  </NModal>

  <!-- 列表行 新增/编辑 子弹窗 -->
  <NModal v-model:show="showRowModal" preset="card" :title="editingRowIndex === null ? '新增一行' : '编辑行'" style="width: 560px" :mask-closable="false">
    <NForm label-placement="top" style="max-height: 420px; overflow-y: auto">
      <NFormItem v-for="f in fields" :key="f.name" :label="(f.label || f.name) + (f.required ? ' *' : '')">
        <NInput v-if="f.fieldType === 'string'" v-model:value="rowFormValues[f.name]" :placeholder="f.remark || ('请输入 ' + (f.label || f.name))" />
        <NInputNumber v-else-if="f.fieldType === 'number'" v-model:value="rowFormValues[f.name]" :placeholder="f.remark || ('请输入 ' + (f.label || f.name))" style="width: 100%" :show-button="false" />
        <NSwitch v-else v-model:value="rowFormValues[f.name]" />
        <template v-if="f.remark && f.fieldType !== 'string'">
          <div style="color:#999;font-size:11px">{{ f.remark }}</div>
        </template>
      </NFormItem>
    </NForm>
    <template #footer>
      <NSpace justify="end" style="width:100%">
        <NButton size="small" @click="showRowModal = false">取消</NButton>
        <NButton size="small" type="primary" @click="handleRowConfirm">确定</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
