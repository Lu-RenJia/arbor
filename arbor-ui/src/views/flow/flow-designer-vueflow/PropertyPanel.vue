<script setup lang="ts">
/**
 * 右侧属性面板：开始节点入参 / 节点属性（按类型的 config 表单）/ 结果返回展示
 * 表单结构与旧设计器保持一致，直接编辑传入的 DesignerNode（config 为 JSON 字符串）。
 */
import { ref, computed, watch } from 'vue'
import { NButton, NSpace, NInput, NFormItem, NSelect, NTag, NForm, NRadioGroup, NRadioButton, NInputNumber, NSwitch, NUpload, NCheckbox, useMessage } from 'naive-ui'
import { formatDuration } from '../../../utils/format'
import { normalizeResult, toPasteText, textStats } from '../../../utils/resultExport'
import { NODE_TYPE_LABELS, tagTypeOf, tagColorOf } from './useFlowGraph'
import type { DesignerNode } from './useFlowGraph'
import { getFlowSchedule, saveFlowSchedule, toggleFlowSchedule, type FlowScheduleVO } from '../../../api/flowSchedule'
import CronBuilderModal from './CronBuilderModal.vue'
import ParamSchemaConfigModal from './ParamSchemaConfigModal.vue'
import type { ParamSchema } from './useFlowGraph'

const props = defineProps<{
  node: DesignerNode | null
  showStartParams: boolean
  showExecPanel: boolean
  dbScriptOptions: any[]
  psOptions: any[]
  apiOptions: any[]
  redisScriptOptions: any[]
  flowOptions: any[]
  /** 当前流程的内联资源 */
  inlineResources: { dbScripts: any[]; programScripts: any[]; apiConfigs: any[]; redisScripts: any[] }
  /** 选中节点在测试结果中的执行结果 */
  nodeResult: any
  /** 选中节点的示例输出（undefined = 无示例） */
  exampleData: any
  /** 当前节点是否存在可内联编辑的脚本 */
  hasEditableScript: boolean
  /** 当前选中节点是否分支节点（分支节点不支持停止条件） */
  isBranchNode: boolean
  /** 当前流程 ID（新建未保存时为 null） */
  flowId: number | null
  /** 当前用户是否管理员（仅 admin 可配置定时任务） */
  isAdmin: boolean
}>()

const emit = defineEmits<{ (e: 'edit-script'): void; (e: 'create-script', inline?: boolean): void; (e: 'edit-api'): void; (e: 'create-api', inline?: boolean): void; (e: 'edit-redis'): void; (e: 'create-redis', inline?: boolean): void; (e: 'edit-flow'): void; (e: 'refresh-graph'): void; (e: 'refresh-options'): void; (e: 'promote', type: string, id: number): void; (e: 'demote', type: string, id: number): void }>()

const startInputParams = defineModel<string>('startInputParams', { default: '' })
const startFileUploadEnabled = defineModel<boolean>('startFileUploadEnabled', { default: false })
const startTestFile = defineModel<File | null>('startTestFile', { default: null })
const startExampleEnabled = defineModel<boolean>('startExampleEnabled', { default: false })
const startOutputType = defineModel<string>('startOutputType', { default: 'JSON' })
defineModel<number | null>('startExampleFileId', { default: null })
const startExampleFileName = defineModel<string>('startExampleFileName', { default: '' })
/** 暂存的示例文件（选择后不立即上传，点击“保存流程”时由父组件统一上传） */
const startExampleStagedFile = defineModel<File | null>('startExampleStagedFile', { default: null })
/** 参数表单 schema（未配置时为 null） */
const startParamSchema = defineModel<ParamSchema | null>('startParamSchema', { default: null })

/** 参数表单配置弹窗 */
const showParamSchemaModal = ref(false)
function onParamSchemaConfirm(schema: ParamSchema | null) {
  startParamSchema.value = schema
}

const message = useMessage()

/** 数据显示模式（有示例时显示切换按钮） */
const nodeDataViewMode = ref<'live' | 'example'>('live')
/** 结果返回的展示格式：false=JSON（默认），true=可粘贴文本 */
const nodeTextMode = ref(false)
/** 文本模式是否输出表头 */
const nodeTextHeader = ref(true)
watch(() => props.node?.uid, () => {
  nodeDataViewMode.value = 'live'
  nodeTextMode.value = false
  nodeTextHeader.value = true
})

/** 当前展示的原始数据（实时结果 / 示例） */
const currentNodeData = computed(() => nodeDataViewMode.value === 'example' ? props.exampleData : props.nodeResult?.data)
/** 文本模式的粘贴文本：显示与复制同一份内容 */
const nodeTextPaste = computed(() => toPasteText(currentNodeData.value, nodeTextHeader.value))
const nodeTextStats = computed(() => textStats(nodeTextPaste.value))
const nodeHasHeaderOption = computed(() => normalizeResult(currentNodeData.value).columns.length > 0)

function getNodeConfig(field: string): any {
  if (!props.node) return null
  try { return JSON.parse(props.node.config)[field] } catch { return null }
}

function updateNodeConfig(field: string, value: any) {
  if (!props.node) return
  let cfg: any = {}
  try { cfg = JSON.parse(props.node.config) } catch { cfg = {} }
  cfg[field] = value
  props.node.config = JSON.stringify(cfg)
}

// ========== 内联/引用模式 ==========
/** 获取当前节点的资源模式（默认 inline） */
function getResourceMode(): 'inline' | 'ref' {
  return getNodeConfig('resourceMode') || 'inline'
}
function setResourceMode(mode: 'inline' | 'ref') {
  updateNodeConfig('resourceMode', mode)
  // 切换模式时清除当前资源选择，避免内联资源ID残留在引用下拉框（或反之）
  if (!props.node) return
  const type = props.node.nodeType
  if (type === 'REF_DB_SCRIPT') updateNodeConfig('dbScriptId', null)
  else if (type === 'REF_PROGRAM_SCRIPT') updateNodeConfig('programScriptId', null)
  else if (type === 'REF_API') updateNodeConfig('apiConfigId', null)
  else if (type === 'REDIS') updateNodeConfig('redisConfigId', null)
}

/** 查找当前节点配置的内联资源对象（用于显示摘要） */
function getInlineResource(): any {
  if (!props.node) return null
  const type = props.node.nodeType
  if (type === 'REF_DB_SCRIPT') {
    const id = getNodeConfig('dbScriptId')
    return id != null ? (props.inlineResources.dbScripts.find((s: any) => s.id === id) || null) : null
  }
  if (type === 'REF_PROGRAM_SCRIPT') {
    const sid = getNodeConfig('programScriptId')
    return sid != null ? (props.inlineResources.programScripts.find((s: any) => s.scriptId === sid) || null) : null
  }
  if (type === 'REF_API') {
    const id = getNodeConfig('apiConfigId')
    return id != null ? (props.inlineResources.apiConfigs.find((a: any) => a.id === id) || null) : null
  }
  if (type === 'REDIS') {
    const id = getNodeConfig('redisConfigId')
    return id != null ? (props.inlineResources.redisScripts.find((s: any) => s.id === id) || null) : null
  }
  return null
}

/** 查找当前节点配置的全局资源对象（引用模式显示名称） */
function getRefResource(): any {
  if (!props.node) return null
  const type = props.node.nodeType
  if (type === 'REF_DB_SCRIPT') {
    const id = getNodeConfig('dbScriptId')
    return id != null ? (props.dbScriptOptions.find((s: any) => s.id === id) || null) : null
  }
  if (type === 'REF_PROGRAM_SCRIPT') {
    const sid = getNodeConfig('programScriptId')
    return sid != null ? (props.psOptions.find((s: any) => s.scriptId === sid) || null) : null
  }
  if (type === 'REF_API') {
    const id = getNodeConfig('apiConfigId')
    return id != null ? (props.apiOptions.find((a: any) => a.id === id) || null) : null
  }
  if (type === 'REDIS') {
    const id = getNodeConfig('redisConfigId')
    return id != null ? (props.redisScriptOptions.find((s: any) => s.id === id) || null) : null
  }
  return null
}

/** 内联→全局 */
function handlePromote() {
  if (!props.node) return
  const type = props.node.nodeType
  const res = getInlineResource()
  if (!res) return
  const apiType = type === 'REF_DB_SCRIPT' ? 'db' : type === 'REF_PROGRAM_SCRIPT' ? 'ps' : type === 'REDIS' ? 'redis' : 'api'
  emit('promote', apiType, res.id)
  // promote 后资源变为全局，保留当前资源ID，仅切换模式（不能用 setResourceMode，否则会清空ID）
  updateNodeConfig('resourceMode', 'ref')
}

/** 引用→内联 */
function handleDemote() {
  if (!props.node) return
  const type = props.node.nodeType
  const res = getRefResource()
  if (!res) return
  const apiType = type === 'REF_DB_SCRIPT' ? 'db' : type === 'REF_PROGRAM_SCRIPT' ? 'ps' : type === 'REDIS' ? 'redis' : 'api'
  emit('demote', apiType, res.id)
}

/** 当前选中的 Redis 配置 id / 配置行（配置被删则为 undefined） */
const redisConfigId = computed(() => getNodeConfig('redisConfigId'))
/**
 * 选中配置按资源模式取：内联查流程私有列表、引用查全局列表。
 * 只查全局列表会让内联模式下的摘要卡永远空白（内联资源不在 redisScriptOptions 里）。
 */
const selectedRedisConfig = computed(() => (getResourceMode() === 'inline' ? getInlineResource() : getRefResource()))

const TTL_UNIT_LABELS: Record<string, string> = { SECONDS: '秒', MINUTES: '分钟', HOURS: '小时', DAYS: '天' }

/**
 * 切换 Redis 配置：已挂分支时禁止改选非 GET&SET 配置；
 * 选中时冗余写入 configCommand 供图层/校验判定分支入口，后端执行以 DB 配置行为准。
 */
function onRedisConfigChange(v: any) {
  if (!props.node) return
  const target = v != null ? props.redisScriptOptions.find((s: any) => s.id === v) : null
  if (props.node.branchNodes.length > 0 && (!target || String(target.command).toUpperCase() !== 'GET&SET')) {
    message.warning('该 Redis 节点已配置分支，只能选择 GET&SET 命令的配置；请先清空分支节点')
    return
  }
  updateNodeConfig('redisConfigId', v)
  updateNodeConfig('configCommand', target ? target.command : null)
  emit('refresh-graph')
}

/** 切换停止条件开关：影响节点卡角标显示，通知父组件重建图 */
function onStopEnabledChange(v: boolean) {
  updateNodeConfig('stopEnabled', v)
  emit('refresh-graph')
}

/** 切换正常停止开关 */
function onNormalStopEnabledChange(v: boolean) {
  updateNodeConfig('normalStopEnabled', v)
  emit('refresh-graph')
}

/** 测试文件选择（仅本地保存，不实际上传） */
function onTestFileChange({ file }: { file: any }) {
  startTestFile.value = file?.file || null
}

/** 示例文件选择（仅暂存到本地，保存流程时才真正上传） */
const exampleUploadKey = ref(0)
function onExampleFileChange({ file }: { file: any }) {
  const raw = file?.file || null
  if (raw) {
    startExampleStagedFile.value = raw
    exampleUploadKey.value++ // 重置 NUpload 内部状态，避免 max 计数导致再次选择无响应
  }
}

// ========== 脚本新建/编辑：全部委托父组件弹出与独立页一致的共享弹窗 ==========

/** 复制当前可见的节点数据：文本模式复制粘贴文本，JSON 模式复制显示的 JSON */
async function copyNodeData() {
  const data = currentNodeData.value
  if (data === null || data === undefined) { message.warning('没有可复制的内容'); return }
  const text = nodeTextMode.value
    ? nodeTextPaste.value
    : (typeof data === 'string' ? data : JSON.stringify(data, null, 2))
  if (!text) { message.warning('没有可复制的内容'); return }
  try { await navigator.clipboard.writeText(text); message.success('已复制') } catch { message.error('复制失败') }
}

// ========== 定时任务配置 ==========
const schedule = ref<FlowScheduleVO | null>(null)
const scheduleLoading = ref(false)
const showCronModal = ref(false)
/** 表单态：cron 表达式 / 次数限制开关 / 次数上限 */
const schedCron = ref('')
const schedLimitEnabled = ref(false)
const schedMaxCount = ref<number | null>(null)
const schedSaving = ref(false)
const schedToggling = ref(false)

async function loadSchedule() {
  if (!props.flowId) { schedule.value = null; return }
  scheduleLoading.value = true
  try {
    const res: any = await getFlowSchedule(props.flowId)
    schedule.value = res || null
    if (res) {
      schedCron.value = res.cronExpr || ''
      schedLimitEnabled.value = res.maxExecCount != null
      schedMaxCount.value = res.maxExecCount
    } else {
      schedCron.value = ''
      schedLimitEnabled.value = false
      schedMaxCount.value = null
    }
  } catch { schedule.value = null }
  finally { scheduleLoading.value = false }
}

watch(() => props.flowId, loadSchedule, { immediate: true })

/** 从 CronBuilderModal 确认返回 */
function onCronConfirm(cron: string) {
  schedCron.value = cron
}

/** 保存调度配置（cron + 次数限制） */
async function saveSchedule() {
  if (!props.flowId) return
  if (!schedCron.value.trim()) { message.warning('请先配置 Cron 表达式'); return }
  schedSaving.value = true
  try {
    await saveFlowSchedule(props.flowId, {
      cronExpr: schedCron.value.trim(),
      maxExecCount: schedLimitEnabled.value ? schedMaxCount.value : null
    })
    message.success('定时任务配置已保存')
    await loadSchedule()
  } catch { /* handled by interceptor */ }
  finally { schedSaving.value = false }
}

/** 启用/停用 */
async function onScheduleToggle(enabled: boolean) {
  if (!props.flowId) return
  schedToggling.value = true
  try {
    await toggleFlowSchedule(props.flowId, enabled)
    message.success(enabled ? '定时任务已启用' : '定时任务已停用')
    await loadSchedule()
  } catch { /* handled by interceptor */ }
  finally { schedToggling.value = false }
}

function fmtTime(t: string | null) {
  return t ? t.replace('T', ' ') : '—'
}
</script>

<template>
  <div v-if="node || showStartParams" :key="node?.uid || 'start'" style="width: 300px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; flex-shrink: 0">
    <!-- 开始节点入参 -->
    <div v-if="showStartParams && !node" style="background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); flex-shrink: 0">
      <h4 style="margin: 0 0 12px; font-size: 14px">🚀 开始节点 — 流程入参</h4>
      <NForm label-placement="top">
        <NFormItem label="_input（流程入参）">
          <NInput
            v-model:value="startInputParams"
            type="textarea"
            :rows="4"
            placeholder='字符串 hello  |  数字 123  |  对象 {"key":"val"}  |  数组 [1,2,3]'
            style="font-family: monospace; font-size: 12px"
          />
        </NFormItem>
        <div style="color: #999; font-size: 11px; line-height: 1.6">
          流程中任意节点可用 <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_input}</code> 获取完整入参<br/>
          对象属性：<code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_input.key}</code><br/>
          数组下标：<code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_input[0]}</code>
        </div>
        <div style="color: #999; font-size: 11px; line-height: 1.6; margin-top: 6px">
          内置环境变量（无需声明）<br/>
          当前环境ID：<code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_env}</code><br/>
          当前环境编码：<code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_env_code}</code><br/>
          Groovy 脚本内直接写裸变量 <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">_env</code>（数字类型）
        </div>

        <!-- 参数表单配置 -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; padding-top: 10px; border-top: 1px dashed #e8e8e8">
          <span style="font-size: 13px; font-weight: 600">📝 参数表单</span>
          <NButton size="small" @click="showParamSchemaModal = true">{{ startParamSchema ? '已配置 · 修改' : '配置' }}</NButton>
        </div>
        <div v-if="startParamSchema" style="color:#18a058;font-size:11px;line-height:1.6">
          {{ startParamSchema.type === 'list' ? '列表' : '对象' }} · {{ startParamSchema.fields.length }} 个字段：
          {{ startParamSchema.fields.map(f => f.label || f.name).join('、') }}
        </div>
        <div v-else style="color: #999; font-size: 11px">配置后使用应用时可按表单录入参数，无需手写 JSON</div>

        <!-- 文件上传配置 -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; padding-top: 10px; border-top: 1px dashed #e8e8e8">
          <span style="font-size: 13px; font-weight: 600">📎 启用文件上传</span>
          <NSwitch size="small" v-model:value="startFileUploadEnabled" />
        </div>
        <div style="color: #999; font-size: 11px">开启后应用执行时可上传文件作为流程输入</div>

        <template v-if="startFileUploadEnabled">
          <!-- 测试文件（仅流程测试用，不绑定应用） -->
          <NFormItem label="测试文件" style="margin-top:8px">
            <NUpload :max="1" accept=".xlsx,.xls,.csv,.json,.txt,.xml" :default-upload="false" @change="onTestFileChange">
              <NButton size="small" :type="startTestFile ? 'primary' : 'default'">{{ startTestFile ? startTestFile.name : '选择测试文件' }}</NButton>
            </NUpload>
            <div style="color:#999;font-size:11px;margin-top:2px">仅用于"测试流程"，绑定到应用后不会使用此文件</div>
          </NFormItem>

          <div style="display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; padding-top: 10px; border-top: 1px dashed #e8e8e8">
            <span style="font-size: 13px; font-weight: 600">📥 启用示例文件</span>
            <NSwitch size="small" v-model:value="startExampleEnabled" />
          </div>
          <template v-if="startExampleEnabled">
            <NFormItem label="示例文件">
              <div v-if="startExampleStagedFile" style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
                <span style="font-size:12px;color:#f0a020">📄 {{ startExampleStagedFile.name }}</span>
                <NTag size="tiny" :bordered="false" type="warning">待保存</NTag>
                <NButton size="tiny" quaternary @click="startExampleStagedFile = null">✕</NButton>
              </div>
              <div v-else-if="startExampleFileName" style="display:flex;align-items:center;margin-bottom:4px">
                <span style="font-size:12px;color:#18a058">📄 {{ startExampleFileName }}</span>
              </div>
              <NUpload
                :key="exampleUploadKey"
                :default-upload="false"
                :show-file-list="false"
                accept=".xlsx,.xls,.csv,.json,.txt,.xml"
                @change="onExampleFileChange"
              >
                <NButton size="small">{{ (startExampleStagedFile || startExampleFileName) ? '重新选择' : '选择文件' }}</NButton>
              </NUpload>
              <div style="color:#999;font-size:11px;margin-top:2px">选择后点击“保存流程”才会生效；应用中可下载此文件</div>
            </NFormItem>
          </template>
        </template>

        <div style="display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; padding-top: 10px; border-top: 1px dashed #e8e8e8">
          <span style="font-size: 13px; font-weight: 600">📤 输出格式</span>
          <NRadioGroup size="small" v-model:value="startOutputType">
            <NRadioButton value="JSON">JSON</NRadioButton>
            <NRadioButton value="FILE">文件下载</NRadioButton>
          </NRadioGroup>
        </div>
        <div style="color: #999; font-size: 11px">
          <template v-if="startOutputType === 'JSON'">JSON 模式：配合字段映射渲染为表格</template>
          <template v-else>文件模式：执行结果以文件形式提供下载</template>
        </div>
      </NForm>
    </div>

    <!-- 定时任务配置（START 面板最下方） -->
    <div v-if="showStartParams && !node" style="background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); flex-shrink: 0">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
        <h4 style="margin: 0; font-size: 14px">⏰ 定时任务</h4>
        <NTag v-if="schedule" size="tiny" :type="schedule.enabled === 1 ? 'success' : 'default'" :bordered="false">{{ schedule.enabled === 1 ? '已启用' : '已停用' }}</NTag>
      </div>

      <!-- 新建未保存的流程 -->
      <div v-if="!flowId" style="color:#999;font-size:12px">请先保存流程，再配置定时任务</div>

      <template v-else>
        <!-- Cron 表达式 -->
        <NFormItem label="Cron 表达式" style="margin-bottom:8px">
          <div style="display:flex;gap:6px;width:100%">
            <NInput :value="schedCron" readonly placeholder="未配置" size="small" style="font-family:monospace;flex:1" />
            <NButton size="small" :disabled="!isAdmin" @click="showCronModal = true">配置</NButton>
          </div>
        </NFormItem>

        <!-- 执行次数限制 -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin: 4px 0 6px">
          <span style="font-size: 13px">执行次数限制</span>
          <NSwitch size="small" :value="schedLimitEnabled" :disabled="!isAdmin" @update:value="(v: boolean) => schedLimitEnabled = v" />
        </div>
        <NInputNumber v-if="schedLimitEnabled" v-model:value="schedMaxCount" :min="1" size="small" placeholder="执行次数上限" style="width:100%;margin-bottom:6px" :disabled="!isAdmin" />

        <!-- 保存 -->
        <NButton type="primary" size="small" block :loading="schedSaving" :disabled="!isAdmin" @click="saveSchedule" style="margin-bottom:8px">保存配置</NButton>

        <!-- 启用/停用开关 -->
        <div v-if="schedule" style="display: flex; justify-content: space-between; align-items: center; padding-top: 8px; border-top: 1px dashed #e8e8e8">
          <span style="font-size: 13px; font-weight: 600">启用调度</span>
          <NSwitch :value="schedule.enabled === 1" :loading="schedToggling" :disabled="!isAdmin" @update:value="(v: boolean) => onScheduleToggle(v)" />
        </div>

        <!-- 执行统计 -->
        <div v-if="schedule" style="color:#999;font-size:11px;line-height:1.8;margin-top:6px">
          已执行：{{ schedule.execCount }} 次<span v-if="schedule.maxExecCount != null"> / 上限 {{ schedule.maxExecCount }} 次</span><br/>
          上次执行：{{ fmtTime(schedule.lastExecTime) }}<br/>
          下次执行：{{ fmtTime(schedule.nextExecTime) }}
        </div>

        <div v-if="!isAdmin" style="color:#f0a020;font-size:11px;margin-top:6px">仅管理员可配置定时任务</div>
      </template>
    </div>

    <!-- 节点属性 -->
    <div v-if="node" style="background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); flex-shrink: 0">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
        <h4 style="margin: 0; font-size: 14px">节点属性</h4>
        <NTag size="tiny" :type="tagTypeOf(node.nodeType)" :color="tagColorOf(node.nodeType)" :bordered="false">{{ NODE_TYPE_LABELS[node.nodeType] || node.nodeType }}</NTag>
      </div>
      <NForm label-placement="top" require-mark-placement="right">
        <NFormItem label="节点编码">
          <NInput v-model:value="node.nodeCode" size="small" />
        </NFormItem>
        <NFormItem label="节点名称">
          <NInput v-model:value="node.nodeName" size="small" />
        </NFormItem>

        <template v-if="node.nodeType === 'REF_DB_SCRIPT'">
          <!-- 模式切换 -->
          <NFormItem label="资源模式">
            <NRadioGroup size="small" :value="getResourceMode()" @update:value="(v: string) => setResourceMode(v as any)">
              <NRadioButton value="inline" :disabled="!flowId">内联</NRadioButton>
              <NRadioButton value="ref">引用</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <!-- 内联模式：摘要 + 编辑 + 新建 + 转为全局 -->
          <template v-if="getResourceMode() === 'inline'">
            <div v-if="!flowId" style="color:#f0a020;font-size:11px;margin-bottom:8px">请先保存流程，再使用内联模式</div>
            <div v-else-if="getInlineResource()" style="background:#f7f9fc;border:1px solid #e8eef7;border-radius:6px;padding:10px 12px;margin-bottom:8px">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-size:13px;font-weight:600">📄 {{ getInlineResource().name }}</span>
                <NTag size="tiny" type="success" :bordered="false">内联</NTag>
              </div>
              <NSpace style="margin-top:8px" :size="6">
                <NButton size="tiny" type="primary" @click="emit('edit-script')">✏️ 编辑</NButton>
                <NButton size="tiny" @click="handlePromote">↑ 转为全局</NButton>
              </NSpace>
            </div>
            <div v-else style="margin-bottom:8px">
              <NButton size="small" type="primary" dashed block @click="emit('create-script', true)">+ 新建内联脚本</NButton>
            </div>
          </template>
          <!-- 引用模式：下拉选择 + 转为内联 -->
          <template v-else>
            <NFormItem label="选择数据库脚本">
              <div style="display:flex;gap:6px">
                <NSelect
                  :value="getNodeConfig('dbScriptId')"
                  @update:value="(v: any) => updateNodeConfig('dbScriptId', v)"
                  :options="dbScriptOptions.map((s: any) => ({ label: s.name, value: s.id }))"
                  placeholder="请选择脚本"
                  filterable
                  style="flex:1"
                />
                <NButton size="tiny" quaternary type="primary" @click="emit('create-script')">+新建</NButton>
              </div>
            </NFormItem>
            <NSpace :size="6" style="margin-top:4px">
              <NButton v-if="hasEditableScript" size="small" @click="emit('edit-script')">✏️ 编辑此脚本</NButton>
              <NButton v-if="getNodeConfig('dbScriptId')" size="small" @click="handleDemote">↓ 转为内联</NButton>
            </NSpace>
          </template>
        </template>

        <template v-if="node.nodeType === 'REF_PROGRAM_SCRIPT'">
          <!-- 模式切换 -->
          <NFormItem label="资源模式">
            <NRadioGroup size="small" :value="getResourceMode()" @update:value="(v: string) => setResourceMode(v as any)">
              <NRadioButton value="inline" :disabled="!flowId">内联</NRadioButton>
              <NRadioButton value="ref">引用</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <!-- 内联模式 -->
          <template v-if="getResourceMode() === 'inline'">
            <div v-if="!flowId" style="color:#f0a020;font-size:11px;margin-bottom:8px">请先保存流程，再使用内联模式</div>
            <div v-else-if="getInlineResource()" style="background:#f7f9fc;border:1px solid #e8eef7;border-radius:6px;padding:10px 12px;margin-bottom:8px">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-size:13px;font-weight:600">📄 {{ getInlineResource().name }}</span>
                <NTag size="tiny" type="success" :bordered="false">内联</NTag>
              </div>
              <NSpace style="margin-top:8px" :size="6">
                <NButton size="tiny" type="primary" @click="emit('edit-script')">✏️ 编辑</NButton>
                <NButton size="tiny" @click="handlePromote">↑ 转为全局</NButton>
              </NSpace>
            </div>
            <div v-else style="margin-bottom:8px">
              <NButton size="small" type="primary" dashed block @click="emit('create-script', true)">+ 新建内联脚本</NButton>
            </div>
          </template>
          <!-- 引用模式 -->
          <template v-else>
            <NFormItem label="选择程序脚本">
              <div style="display:flex;gap:6px">
                <NSelect
                  :value="getNodeConfig('programScriptId')"
                  @update:value="(v: any) => updateNodeConfig('programScriptId', v)"
                  :options="psOptions.map((s: any) => ({ label: s.name + ' (' + s.scriptId + ')', value: s.scriptId }))"
                  placeholder="请选择程序脚本"
                  filterable
                  style="flex:1"
                />
                <NButton size="tiny" quaternary type="primary" @click="emit('create-script')">+新建</NButton>
              </div>
            </NFormItem>
            <NSpace :size="6" style="margin-top:4px">
              <NButton v-if="hasEditableScript" size="small" @click="emit('edit-script')">✏️ 编辑此脚本</NButton>
              <NButton v-if="getNodeConfig('programScriptId')" size="small" @click="handleDemote">↓ 转为内联</NButton>
            </NSpace>
          </template>
        </template>

        <template v-if="node.nodeType === 'REF_API'">
          <!-- 模式切换 -->
          <NFormItem label="资源模式">
            <NRadioGroup size="small" :value="getResourceMode()" @update:value="(v: string) => setResourceMode(v as any)">
              <NRadioButton value="inline" :disabled="!flowId">内联</NRadioButton>
              <NRadioButton value="ref">引用</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <!-- 内联模式 -->
          <template v-if="getResourceMode() === 'inline'">
            <div v-if="!flowId" style="color:#f0a020;font-size:11px;margin-bottom:8px">请先保存流程，再使用内联模式</div>
            <div v-else-if="getInlineResource()" style="background:#f7f9fc;border:1px solid #e8eef7;border-radius:6px;padding:10px 12px;margin-bottom:8px">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-size:13px;font-weight:600">🌐 {{ getInlineResource().name }} <NTag size="tiny" :bordered="false">{{ getInlineResource().method }}</NTag></span>
                <NTag size="tiny" type="success" :bordered="false">内联</NTag>
              </div>
              <NSpace style="margin-top:8px" :size="6">
                <NButton size="tiny" type="primary" @click="emit('edit-api')">✏️ 编辑</NButton>
                <NButton size="tiny" @click="handlePromote">↑ 转为全局</NButton>
              </NSpace>
            </div>
            <div v-else style="margin-bottom:8px">
              <NButton size="small" type="primary" dashed block @click="emit('create-api', true)">+ 新建内联接口</NButton>
            </div>
          </template>
          <!-- 引用模式 -->
          <template v-else>
            <NFormItem label="选择接口">
              <div style="display:flex;gap:6px">
                <NSelect
                  :value="getNodeConfig('apiConfigId')"
                  @update:value="(v: any) => updateNodeConfig('apiConfigId', v)"
                  :options="apiOptions.map((a: any) => ({ label: a.name + ' [' + a.method + ']', value: a.id }))"
                  placeholder="请选择接口"
                  filterable
                  style="flex:1"
                />
                <NButton size="tiny" quaternary type="primary" @click="emit('create-api')">+新建</NButton>
              </div>
            </NFormItem>
            <NSpace :size="6" style="margin-top:4px">
              <NButton v-if="getNodeConfig('apiConfigId')" size="small" @click="emit('edit-api')">✏️ 编辑此接口</NButton>
              <NButton v-if="getNodeConfig('apiConfigId')" size="small" @click="handleDemote">↓ 转为内联</NButton>
            </NSpace>
          </template>
        </template>

        <template v-if="node.nodeType === 'REF_FLOW'">
          <NFormItem label="选择流程">
            <NSelect
              :value="getNodeConfig('flowId')"
              @update:value="(v: any) => updateNodeConfig('flowId', v)"
              :options="flowOptions.map((f: any) => ({ label: f.name, value: f.id }))"
              placeholder="请选择要引用的流程"
              filterable
            />
          </NFormItem>
          <NFormItem label="入参（可选）">
            <NInput
              :value="getNodeConfig('inputParams')"
              @update:value="(v: string) => updateNodeConfig('inputParams', v)"
              type="textarea"
              :rows="3"
              placeholder='支持 ${} 表达式，如 ${node1} 或 ${_input}，传入子流程作为 _input'
              style="font-family: monospace; font-size: 12px"
            />
          </NFormItem>
          <NSpace :size="6" style="margin-top:4px">
            <NButton v-if="getNodeConfig('flowId')" size="small" @click="emit('edit-flow')">✏️ 编辑此流程</NButton>
          </NSpace>
          <div style="color: #999; font-size: 11px; line-height: 1.7">
            子流程最后一个节点的输出作为本节点结果<br/>
            入参传入子流程后可用 <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_input}</code> 获取<br/>
            子流程内的 <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">_env</code> / <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${_env}</code> 继承父流程的执行环境
          </div>
        </template>

        <template v-if="node.nodeType === 'REDIS'">
          <!-- 模式切换：与 REF_DB_SCRIPT 一致，内联=流程专属，引用=全局共享 -->
          <NFormItem label="资源模式">
            <NRadioGroup size="small" :value="getResourceMode()" @update:value="(v: string) => setResourceMode(v as any)">
              <NRadioButton value="inline" :disabled="!flowId">内联</NRadioButton>
              <NRadioButton value="ref">引用</NRadioButton>
            </NRadioGroup>
          </NFormItem>

          <!-- 内联模式：未选配置时给新建入口（已选中时走下方共用摘要卡） -->
          <template v-if="getResourceMode() === 'inline'">
            <div v-if="!flowId" style="color:#f0a020;font-size:11px;margin-bottom:8px">请先保存流程，再使用内联模式</div>
            <div v-else-if="!getInlineResource()" style="margin-bottom:8px">
              <NButton size="small" type="primary" dashed block @click="emit('create-redis', true)">+ 新建内联 Redis 配置</NButton>
            </div>
          </template>
          <!-- 引用模式：全局配置下拉 + 新建 -->
          <template v-else>
            <NFormItem label="Redis配置">
              <div style="display:flex;gap:6px">
                <NSelect :value="redisConfigId" @update:value="onRedisConfigChange"
                  :options="redisScriptOptions.map((s: any) => ({ label: s.name + ' [' + s.command + ']', value: s.id }))"
                  placeholder="选择已配置的Redis" filterable clearable style="flex:1" />
                <NButton size="tiny" quaternary type="primary" @click="emit('create-redis')">+新建</NButton>
              </div>
            </NFormItem>
          </template>

          <!-- 配置摘要（两种模式共用）：命令/TTL 来自配置行，key 展示完整形态 -->
          <div v-if="selectedRedisConfig" style="background: #f7f9fc; border: 1px solid #e8eef7; border-radius: 6px; padding: 10px 12px; font-size: 12px; line-height: 1.9; margin-bottom: 8px">
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-size:13px;font-weight:600">🔑 {{ selectedRedisConfig.name }}</span>
              <NTag size="tiny" :type="getResourceMode() === 'inline' ? 'success' : 'info'" :bordered="false">{{ getResourceMode() === 'inline' ? '内联' : '全局' }}</NTag>
            </div>
            <div><span style="color: #999">命令：</span><strong>{{ selectedRedisConfig.command }}</strong></div>
            <!-- 完整 key = {业务前缀}:{redisKey}，与 Redis 里真实存在的 key 一致；
                 旧文案「执行时自动加流程前缀」已不成立，系统不再追加任何前缀 -->
            <div style="word-break: break-all"><span style="color: #999">完整 Key：</span><span style="font-family:monospace">{{ selectedRedisConfig.fullKey || selectedRedisConfig.redisKey }}</span></div>
            <div v-if="selectedRedisConfig.ttl"><span style="color: #999">过期时间：</span>{{ selectedRedisConfig.ttl }} {{ TTL_UNIT_LABELS[selectedRedisConfig.ttlUnit || 'SECONDS'] }}</div>
            <div v-if="String(selectedRedisConfig.command).toUpperCase() === 'GET&SET'" style="color: #9254de">
              未命中时执行右侧分支链，最后一个分支节点结果作为本节点返回值并写入缓存
            </div>
            <NSpace style="margin-top:8px" :size="6">
              <NButton size="tiny" type="primary" @click="emit('edit-redis')">✏️ 编辑</NButton>
              <NButton v-if="getResourceMode() === 'inline'" size="tiny" @click="handlePromote">↑ 转为全局</NButton>
              <NButton v-else size="tiny" @click="handleDemote">↓ 转为内联</NButton>
            </NSpace>
          </div>
          <div v-else-if="redisConfigId != null" style="color: #d03050; font-size: 12px; line-height: 1.5; margin-bottom: 8px">
            ⚠️ 所选 Redis 配置（id={{ redisConfigId }}）已不存在，请重新选择，否则执行会失败
          </div>

          <NFormItem v-if="String(selectedRedisConfig?.command || '').toUpperCase() === 'GET&SET'" label="提取路径">
            <NInput :value="getNodeConfig('cacheExtractPath')" @update:value="(v: string) => updateNodeConfig('cacheExtractPath', v)" placeholder="可选，如 body.access_token" />
            <template #feedback>
              <span style="font-size: 11px; color: #999">从分支结果中按路径提取字段存入缓存，不填则存整个结果</span>
            </template>
          </NFormItem>
          <div v-if="node.branchNodes.length > 0 && String(selectedRedisConfig?.command || '').toUpperCase() !== 'GET&SET'" style="color: #d03050; font-size: 11px; line-height: 1.5">
            ⚠️ 已配置分支流程，但分支仅支持 GET&SET 命令的配置
          </div>
        </template>

        <!-- 停止条件（通用，仅主线节点；分支节点不支持） -->
        <template v-if="!isBranchNode">
          <!-- 异常停止 -->
          <div style="display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; padding-top: 10px; border-top: 1px dashed #e8e8e8">
            <span style="font-size: 13px; font-weight: 600">⚠️ 异常停止</span>
            <NSwitch size="small" :value="!!getNodeConfig('stopEnabled')" @update:value="onStopEnabledChange" />
          </div>
          <template v-if="getNodeConfig('stopEnabled')">
            <NFormItem label="条件表达式" required>
              <NInput
                :value="getNodeConfig('stopCondition')"
                @update:value="(v: string) => updateNodeConfig('stopCondition', v)"
                type="textarea"
                :rows="3"
                placeholder="data == null || data.isEmpty()"
                style="font-family: monospace; font-size: 12px"
              />
            </NFormItem>
            <NFormItem label="停止提示信息">
              <NInput
                :value="getNodeConfig('stopMessage')"
                @update:value="(v: string) => updateNodeConfig('stopMessage', v)"
                placeholder="满足停止条件，流程停止"
              />
            </NFormItem>
            <div style="color: #f0a020; font-size: 11px; line-height: 1.7">
              ⚠️ 触发后流程异常停止（<span style="color:#f0a020;font-weight:600">黄色标识</span>），评估顺序优先于正常停止<br/>
              <code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">data</code> = 本节点执行结果；<code style="background:#f0f0f0;padding:1px 4px;border-radius:2px">${}</code> 可引用其他节点/全局参数
            </div>
          </template>

          <!-- 正常停止 -->
          <div style="display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; padding-top: 10px; border-top: 1px dashed #e8e8e8">
            <span style="font-size: 13px; font-weight: 600">✅ 正常停止</span>
            <NSwitch size="small" :value="!!getNodeConfig('normalStopEnabled')" @update:value="onNormalStopEnabledChange" />
          </div>
          <template v-if="getNodeConfig('normalStopEnabled')">
            <NFormItem label="条件表达式" required>
              <NInput
                :value="getNodeConfig('normalStopCondition')"
                @update:value="(v: string) => updateNodeConfig('normalStopCondition', v)"
                type="textarea"
                :rows="3"
                placeholder="data == null || data.isEmpty()"
                style="font-family: monospace; font-size: 12px"
              />
            </NFormItem>
            <NFormItem label="停止提示信息">
              <NInput
                :value="getNodeConfig('normalStopMessage')"
                @update:value="(v: string) => updateNodeConfig('normalStopMessage', v)"
                placeholder="满足停止条件，流程停止"
              />
            </NFormItem>
            <div style="color: #2080f0; font-size: 11px; line-height: 1.7">
              ✅ 触发后流程正常停止（<span style="color:#2080f0;font-weight:600">蓝色标识</span>），仅在异常停止条件未触发时评估
            </div>
          </template>
        </template>
      </NForm>
    </div>

    <!-- 节点返回数据 / 示例结果 -->
    <div v-if="nodeResult || exampleData !== undefined" style="background: #fff; border-radius: 8px; padding: 12px 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); flex: 1; min-height: 180px; display: flex; flex-direction: column">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; flex-shrink: 0">
        <span style="font-weight: 600; font-size: 13px">📋 结果返回</span>
        <NSpace :size="6" align="center">
          <span v-if="!nodeResult || nodeDataViewMode === 'example'" style="font-size: 11px; color: #999; background: #f0f0f0; padding: 1px 6px; border-radius: 3px">示例</span>
          <NRadioGroup v-if="nodeResult && exampleData !== undefined" v-model:value="nodeDataViewMode" size="small">
            <NRadioButton value="live">数据</NRadioButton>
            <NRadioButton value="example">示例</NRadioButton>
          </NRadioGroup>
          <NCheckbox v-if="currentNodeData !== undefined && currentNodeData !== null" v-model:checked="nodeTextMode" size="small">文本</NCheckbox>
          <NButton v-if="currentNodeData !== undefined && currentNodeData !== null" size="tiny" @click="copyNodeData" quaternary>📋 复制</NButton>
        </NSpace>
      </div>
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-shrink: 0; font-size: 11px; color: #999">
        <template v-if="nodeDataViewMode === 'live' && nodeResult">
          <NTag :type="nodeResult.status === 'STOPPED' ? 'warning' : (nodeResult.success ? 'success' : 'error')" size="tiny">{{ nodeResult.status === 'STOPPED' ? '流程停止' : (nodeResult.success ? '成功' : '失败') }}</NTag>
          <span>{{ formatDuration(nodeResult.duration) }}</span>
        </template>
        <template v-if="nodeTextMode">
          <NCheckbox v-if="nodeHasHeaderOption" v-model:checked="nodeTextHeader" size="small">含表头</NCheckbox>
          <span>{{ nodeTextStats.lines }} 行 · {{ nodeTextStats.chars }} 字符</span>
        </template>
      </div>
      <!-- 实时数据 -->
      <template v-if="nodeResult && nodeDataViewMode === 'live'">
        <template v-if="nodeResult.success">
          <textarea v-if="nodeTextMode" readonly :value="nodeTextPaste" wrap="off" spellcheck="false" style="width:100%; flex:1; min-height:60px; resize:none; border:1px solid #f0f0f0; border-radius:4px; padding:10px; font-size:12px; font-family:monospace; line-height:1.5; background:#fafafa; box-sizing:border-box; white-space:pre; overflow:auto" />
          <pre v-else style="margin:0; flex:1; overflow:auto; background:#fafafa; padding:10px; border-radius:4px; font-size:12px; border:1px solid #f0f0f0">{{ typeof nodeResult.data === 'string' ? nodeResult.data : JSON.stringify(nodeResult.data, null, 2) }}</pre>
        </template>
        <div v-else-if="nodeResult.status === 'STOPPED'" style="color:#d48806; font-size:12px; padding:8px; background:#fffbe6; border-radius:4px">流程已停止，本节点未执行</div>
        <div v-else style="color:#cf1322; font-size:12px; padding:8px; background:#fff2f0; border-radius:4px">{{ nodeResult.errorMessage || '未知错误' }}</div>
      </template>
      <!-- 示例数据 -->
      <template v-else>
        <textarea v-if="nodeTextMode" readonly :value="nodeTextPaste" wrap="off" spellcheck="false" style="width:100%; flex:1; min-height:60px; resize:none; border:1px solid #f0f0f0; border-radius:4px; padding:10px; font-size:12px; font-family:monospace; line-height:1.5; background:#fafafa; box-sizing:border-box; white-space:pre; overflow:auto" />
        <pre v-else style="margin:0; flex:1; overflow:auto; background:#fafafa; padding:10px; border-radius:4px; font-size:12px; border:1px solid #f0f0f0">{{ typeof exampleData === 'string' ? exampleData : JSON.stringify(exampleData, null, 2) }}</pre>
      </template>
    </div>
    <div v-else-if="showExecPanel && node" style="background: #fff; border-radius: 8px; padding: 12px 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); flex: 1; min-height: 0; display: flex; align-items: center; justify-content: center">
      <span style="color: #999; font-size: 12px">该节点暂无执行数据</span>
    </div>
  </div>

  <!-- Cron 可视化构建弹窗 -->
  <CronBuilderModal v-model:show="showCronModal" @confirm="onCronConfirm" />

  <!-- 参数表单配置弹窗 -->
  <ParamSchemaConfigModal v-model:show="showParamSchemaModal" :schema="startParamSchema" @confirm="onParamSchemaConfirm" />
</template>
