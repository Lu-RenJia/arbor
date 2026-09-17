<script setup lang="ts">
/**
 * 参数表单 schema 配置弹窗
 * 配置入参形态（对象/列表）与字段列表（英文名/中文名/备注/类型/必填），
 * 确认后写入 START 节点 config.paramSchema，随流程保存与共享流转。
 */
import { ref, watch } from 'vue'
import { NModal, NRadioGroup, NRadioButton, NInput, NSelect, NButton, NSpace, NSwitch, useMessage } from 'naive-ui'
import type { ParamSchema, ParamSchemaField } from './useFlowGraph'

const show = defineModel<boolean>('show', { default: false })
const props = defineProps<{ schema: ParamSchema | null }>()
const emit = defineEmits<{ (e: 'confirm', schema: ParamSchema | null): void }>()

const message = useMessage()

const schemaType = ref<'object' | 'list'>('object')
const fields = ref<ParamSchemaField[]>([])

const fieldTypeOptions = [
  { label: '文本', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔', value: 'boolean' },
]

const mkField = (): ParamSchemaField => ({ name: '', label: '', remark: '', fieldType: 'string', required: false })

/** 打开时用已有配置初始化 */
// immediate 必需：组件若带着 show=true 被新建（父级 v-if 翻转），无 false→true 跳变，
// 非 immediate 的 watch 不会触发，初始化会被静默跳过
watch(show, (v) => {
  if (!v) return
  if (props.schema && props.schema.fields?.length) {
    schemaType.value = props.schema.type === 'list' ? 'list' : 'object'
    fields.value = props.schema.fields.map(f => ({ ...f }))
  } else {
    schemaType.value = 'object'
    fields.value = [mkField()]
  }
}, { immediate: true })

function addField() { fields.value.push(mkField()) }
function removeField(i: number) { fields.value.splice(i, 1) }

/** 校验字段名：非空、不重复、合法标识符（字母/下划线开头，仅含字母数字下划线） */
function validate(): string | null {
  const names = new Set<string>()
  for (const f of fields.value) {
    const name = (f.name || '').trim()
    if (!name) return '存在未填写英文字段名的行'
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) return `字段名「${name}」不合法：须以字母或下划线开头，仅含字母、数字、下划线`
    if (names.has(name)) return `字段名重复：${name}`
    names.add(name)
  }
  return null
}

function handleConfirm() {
  if (!fields.value.length) { message.warning('请至少配置一个字段'); return }
  const err = validate()
  if (err) { message.warning(err); return }
  const schema: ParamSchema = {
    type: schemaType.value,
    fields: fields.value.map(f => ({
      name: f.name.trim(),
      label: (f.label || '').trim() || f.name.trim(),
      remark: (f.remark || '').trim(),
      fieldType: f.fieldType,
      required: !!f.required,
    })),
  }
  emit('confirm', schema)
  show.value = false
}

/** 清除配置（删除 paramSchema） */
function handleClear() {
  emit('confirm', null)
  show.value = false
}
</script>

<template>
  <NModal v-model:show="show" preset="card" title="⚙️ 参数表单配置" style="width: 760px" :mask-closable="false">
    <div style="margin-bottom: 14px">
      <div style="font-size: 13px; font-weight: 600; margin-bottom: 6px">入参形态</div>
      <NRadioGroup v-model:value="schemaType" size="small">
        <NRadioButton value="object">对象</NRadioButton>
        <NRadioButton value="list">列表</NRadioButton>
      </NRadioGroup>
      <div style="color: #999; font-size: 11px; margin-top: 4px">
        对象：填写一组字段后生成 <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">{...}</code>；
        列表：可增删改多行，生成 <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">[{...},...]</code>
      </div>
    </div>

    <div style="font-size: 13px; font-weight: 600; margin-bottom: 6px">字段列表</div>
    <!-- 表头 -->
    <div style="display: grid; grid-template-columns: 1.2fr 1fr 1.2fr 90px 60px 40px; gap: 8px; padding: 0 0 6px; font-size: 12px; color: #666">
      <span>英文字段名 <span style="color:#d03050">*</span></span>
      <span>中文名</span>
      <span>备注</span>
      <span>类型</span>
      <span>必填</span>
      <span></span>
    </div>
    <div style="display: flex; flex-direction: column; gap: 8px; max-height: 380px; overflow-y: auto">
      <div v-for="(f, i) in fields" :key="i" style="display: grid; grid-template-columns: 1.2fr 1fr 1.2fr 90px 60px 40px; gap: 8px; align-items: center">
        <NInput v-model:value="f.name" size="small" placeholder="如 orderId" style="font-family: monospace" />
        <NInput v-model:value="f.label" size="small" placeholder="如 订单号" />
        <NInput v-model:value="f.remark" size="small" placeholder="选填" />
        <NSelect v-model:value="f.fieldType" size="small" :options="fieldTypeOptions" />
        <NSwitch v-model:value="f.required" size="small" />
        <NButton size="small" quaternary type="error" :disabled="fields.length <= 1" @click="removeField(i)">✕</NButton>
      </div>
    </div>

    <NButton size="small" dashed block style="margin-top: 10px" @click="addField">＋ 新增字段</NButton>

    <div style="color: #999; font-size: 11px; margin-top: 10px; line-height: 1.6">
      配置后，使用应用时会出现「表单填写」按钮，按此表单录入参数并自动生成 JSON；共享后查看者也可使用表单，但看不到此配置入口。
    </div>

    <template #footer>
      <NSpace justify="space-between" style="width: 100%">
        <NButton v-if="props.schema" size="small" type="error" ghost @click="handleClear">清除配置</NButton>
        <span v-else></span>
        <NSpace>
          <NButton size="small" @click="show = false">取消</NButton>
          <NButton size="small" type="primary" @click="handleConfirm">确定</NButton>
        </NSpace>
      </NSpace>
    </template>
  </NModal>
</template>
