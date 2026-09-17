<script setup lang="ts">
/**
 * Redis 配置新建/编辑弹窗（可复用组件）。
 * 与独立「Redis 配置」页的弹窗完全一致，供独立页面 RedisScript.vue 与流程设计器
 * 内联编辑/新建共用，保证 UI 与交互一致（对齐 DbScriptEditModal 的做法）。
 *
 * 业务前缀必选：最终落地的 key = {前缀}:{用户填的 Key}，系统不再追加 env/user/flow 任何前缀，
 * 层级管理与溯源全部由前缀承担。故前缀下拉依赖数据源——切数据源时清空已选前缀并重新拉列表，
 * 否则会提交一个不属于该数据源的 prefixId 被后端拒绝（REDIS_PREFIX_DS_MISMATCH）。
 *
 * props.script 非空 = 编辑该配置；为 null = 新建。保存成功后 emit('saved', 保存后的配置对象)。
 */
import { ref, computed, watch } from 'vue'
import { NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NInputNumber, useMessage } from 'naive-ui'
import { createRedisScript, updateRedisScript, testRedisScriptDraft } from '../api/redisScript'
import { getRedisDsPage } from '../api/redisDatasource'
import { getPrefixByDatasource } from '../api/redisKeyPrefix'

const props = defineProps<{
  /** 编辑的配置对象（来自列表行/选项）；null 表示新建 */
  script: any
  /** 可选流程ID，新建为内联资源时绑定 */
  flowId?: number | null
  /** 新建时是否创建为内联资源（绑定 flowId） */
  createInline?: boolean
}>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ (e: 'saved', script: any): void }>()

const message = useMessage()

const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref<Record<string, any>>({ name: '', datasourceId: null, prefixId: null, command: 'GET', redisKey: '', redisValue: '', ttl: null, ttlUnit: 'SECONDS', remark: '' })
const dsOptions = ref<any[]>([])
const prefixOptions = ref<any[]>([])
const prefixLoading = ref(false)
const saving = ref(false)
/** 弹窗内草稿测试结果（undefined=未测试） */
const testResult = ref<any>(undefined)
const testLoading = ref(false)

// GET&SET: 先 GET，流程中未命中走分支后按 key/ttl 回填 SET（弹窗内测试只执行 GET）
const commandOptions = ['GET', 'SET', 'DEL', 'EXPIRE', 'TTL', 'EXISTS', 'GET&SET'].map(c => ({ label: c, value: c }))
const ttlUnitOptions = [
  { label: '秒', value: 'SECONDS' },
  { label: '分钟', value: 'MINUTES' },
  { label: '小时', value: 'HOURS' },
  { label: '天', value: 'DAYS' },
]

function showTtl(cmd: string): boolean { return cmd === 'SET' || cmd === 'EXPIRE' || cmd === 'GET&SET' }

/** 完整 key 预览：与后端 RedisScriptService.buildBusinessKey 同规则，${} 原样保留 */
const fullKeyPreview = computed(() => {
  const p = prefixOptions.value.find((o: any) => o.value === form.value.prefixId)
  if (!p || !form.value.redisKey) return ''
  return `${p.raw.prefix}:${form.value.redisKey}`
})

/** 拉取某数据源下的业务前缀（keepId=true 时保留当前已选，用于编辑回显） */
async function loadPrefixes(datasourceId: number | null, keepId = false) {
  if (!datasourceId) { prefixOptions.value = []; return }
  prefixLoading.value = true
  try {
    const res: any = await getPrefixByDatasource(datasourceId)
    prefixOptions.value = (res || []).map((p: any) => ({
      // label 带上名称便于识别，值相同时靠前缀本身区分
      label: p.name && p.name !== p.prefix ? `${p.prefix}（${p.name}）` : p.prefix,
      value: p.id,
      raw: p,
    }))
    if (!keepId) form.value.prefixId = null
  } catch { prefixOptions.value = [] }
  finally { prefixLoading.value = false }
}

/** 切换数据源：已选前缀属于旧数据源，必须清空（后端会拒绝跨数据源的 prefixId） */
function onDatasourceChange(v: any) {
  form.value.datasourceId = v
  form.value.prefixId = null
  loadPrefixes(v)
}

/** 弹窗打开时按传入对象初始化表单并加载数据源/前缀选项（null=新建空表单） */
// immediate 必需：流程设计器里本组件挂在会翻转的 v-if 上，切换节点类型时组件是带着
// show=true 被新建的，没有 false→true 跳变，非 immediate 的 watch 一次都不会触发，
// 表单会停在初始空值上——表现为数据源/前缀未选中、标题错显「新增」。首次打开必现。
watch(show, async (v) => {
  if (!v) return
  const row = props.script
  if (row) {
    isEdit.value = true; editId.value = row.id
    form.value = {
      name: row.name, datasourceId: row.datasourceId ?? null, prefixId: row.prefixId ?? null,
      command: row.command || 'GET', redisKey: row.redisKey || '', redisValue: row.redisValue || '',
      ttl: row.ttl ?? null, ttlUnit: row.ttlUnit || 'SECONDS', remark: row.remark || '',
    }
  } else {
    isEdit.value = false; editId.value = null
    form.value = { name: '', datasourceId: null, prefixId: null, command: 'GET', redisKey: '', redisValue: '', ttl: null, ttlUnit: 'SECONDS', remark: '' }
  }
  testResult.value = undefined
  prefixOptions.value = []
  try {
    const res: any = await getRedisDsPage({ pageNum: 1, pageSize: 200 })
    dsOptions.value = (res.list || []).map((d: any) => ({ label: d.name, value: d.id }))
  } catch { dsOptions.value = [] }
  // 编辑回显：先拉前缀列表但保留已选的 prefixId，否则下拉会显示成裸 id
  if (form.value.datasourceId) await loadPrefixes(form.value.datasourceId, true)
}, { immediate: true })

/** 表单校验（保存与草稿测试共用：测试也要拼完整 key，缺前缀同样跑不了） */
function validate(): boolean {
  if (!form.value.name?.trim()) { message.warning('请输入名称'); return false }
  if (!form.value.datasourceId) { message.warning('请选择数据源'); return false }
  if (!form.value.prefixId) { message.warning('请选择业务前缀'); return false }
  if (!form.value.redisKey?.trim()) { message.warning('请输入 Redis Key'); return false }
  if (form.value.command === 'GET&SET' && (!form.value.ttl || form.value.ttl <= 0)) {
    message.warning('GET&SET 命令必须设置过期时间(ttl>0)')
    return false
  }
  return true
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    const payload = { ...form.value }
    let saved: any
    if (isEdit.value) saved = await updateRedisScript(editId.value!, payload)
    else {
      // 内联模式新建时绑定 flowId
      if (props.createInline && props.flowId) payload.flowId = props.flowId
      saved = await createRedisScript(payload)
    }
    // 编辑时就地回写传入对象（供设计器选项标签即时刷新）
    if (props.script && saved) Object.assign(props.script, saved)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    show.value = false
    emit('saved', saved ?? { ...payload, id: editId.value })
  } catch { /* 拦截器已统一提示错误 */ }
  finally { saving.value = false }
}

/** 弹窗内测试：按当前表单内容执行，不落库（GET&SET 只执行 GET） */
async function handleDraftTest() {
  if (!validate()) return
  testLoading.value = true
  try {
    const res: any = await testRedisScriptDraft(form.value)
    // 后端 data 为 null 时拦截器返回 undefined，归一为 null 展示 "(nil)"
    testResult.value = res === undefined ? null : res
  } catch (e: any) {
    testResult.value = undefined
    message.error(e?.message || '测试失败')
  } finally { testLoading.value = false }
}

function fmtResult(v: any): string {
  if (v === null || v === undefined) return '(nil) 未命中'
  return typeof v === 'string' ? v : JSON.stringify(v, null, 2)
}
</script>

<template>
  <NModal v-model:show="show" :key="String(isEdit) + String(editId)" preset="card" :title="isEdit ? '编辑Redis配置' : '新增Redis配置'" style="width: 600px" :mask-closable="false">
    <NForm :model="form" label-placement="left" label-width="90" require-mark-placement="right">
      <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="脚本名称" /></NFormItem>
      <NFormItem label="数据源" required>
        <NSelect :value="form.datasourceId" :options="dsOptions" placeholder="选择Redis数据源" filterable @update:value="onDatasourceChange" />
      </NFormItem>
      <NFormItem label="业务前缀" required>
        <NSelect
          v-model:value="form.prefixId"
          :options="prefixOptions"
          :loading="prefixLoading"
          :disabled="!form.datasourceId"
          :placeholder="form.datasourceId ? '选择业务前缀' : '请先选择数据源'"
          filterable
        />
        <template #feedback>
          <span v-if="!form.datasourceId" style="font-size: 11px; color: #999">前缀登记在数据源下，请先选择数据源</span>
          <span v-else style="font-size: 11px; color: #999">最终 key = 前缀 + ":" + 下面的 Key，前缀在数据源页登记</span>
        </template>
      </NFormItem>
      <NFormItem label="命令" required><NSelect v-model:value="form.command" :options="commandOptions" /></NFormItem>
      <NFormItem label="Redis Key" required>
        <NInput v-model:value="form.redisKey" placeholder="支持 ${变量}，如 userId:${userId}" />
        <template #feedback>
          <span v-if="fullKeyPreview" style="font-size: 11px; color: #18a058; font-family: monospace; word-break: break-all">完整 key：{{ fullKeyPreview }}</span>
          <span v-else style="font-size: 11px; color: #999">只写业务部分，不含任何系统前缀</span>
        </template>
      </NFormItem>
      <NFormItem v-if="form.command === 'SET'" label="Value"><NInput v-model:value="form.redisValue" type="textarea" :rows="3" placeholder="支持 ${变量}" /></NFormItem>
      <NFormItem v-if="showTtl(form.command)" label="过期时间" :required="form.command === 'GET&SET'">
        <NInputNumber v-model:value="form.ttl" :min="0" style="flex: 1" placeholder="过期时间" />
        <NSelect v-if="form.command === 'GET&SET'" v-model:value="form.ttlUnit" :options="ttlUnitOptions" style="width: 110px; margin-left: 8px" />
        <span v-else style="margin-left: 8px; color: #999; font-size: 12px">秒</span>
      </NFormItem>
      <div v-if="form.command === 'GET&SET'" style="color: #999; font-size: 12px; margin: -8px 0 12px 90px; line-height: 1.5">
        GET&amp;SET：流程中先 GET，未命中走子分支链路，分支结果作为节点返回值并按此 key/过期时间写入缓存。此处测试只执行 GET。
      </div>
      <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="备注" /></NFormItem>
      <div v-if="testResult !== undefined" style="margin-bottom: 12px">
        <div style="font-size: 12px; color: #666; margin-bottom: 4px">测试结果：</div>
        <pre style="margin: 0; max-height: 160px; overflow: auto; background: #fafafa; padding: 8px 10px; border-radius: 4px; font-size: 12px; border: 1px solid #f0f0f0">{{ fmtResult(testResult) }}</pre>
      </div>
      <NSpace justify="space-between">
        <NButton :loading="testLoading" @click="handleDraftTest" ghost type="warning">▶ 测试</NButton>
        <NSpace>
          <NButton @click="show = false">取消</NButton>
          <NButton type="primary" @click="handleSave" :loading="saving">确定</NButton>
        </NSpace>
      </NSpace>
    </NForm>
  </NModal>
</template>
