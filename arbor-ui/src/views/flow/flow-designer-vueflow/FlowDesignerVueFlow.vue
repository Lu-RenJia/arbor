<script setup lang="ts">
/**
 * Vue Flow 图形化流程设计器
 *
 * 后端数据契约：保存/执行/加载/示例输出。
 * REDIS GET 节点的分支流程以图形化边呈现（缓存未命中 → 分支链）。
 * 布局由节点列表确定性推导（主线纵向、分支挂右侧列），不持久化坐标。
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NSpace, NInput, NFormItem, NTag, NModal, NRadioGroup, NRadioButton, useMessage } from 'naive-ui'
import { VueFlow, Handle, Position } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import { getFlowDetail, saveFlowNodes, getFlowNodes, executeFlow, cancelFlowExecution, updateFlow, saveFlowExamples, getFlowPage, createFlow } from '../../../api/flow'
import { getDbScriptPage } from '../../../api/dbScript'
import { getPsPage } from '../../../api/programScript'
import { getApiPage } from '../../../api/apiConfig'
import { getRedisScriptPage } from '../../../api/redisScript'
import { getEnvActive } from '../../../api/environment'
import { useEnvStore } from '../../../stores/env'
import { getFlowInlineResources, promoteResource, demoteResource } from '../../../api/inlineResource'
import { getFileInfo, uploadFile } from '../../../api/file'
import FlowNodeCard from './FlowNodeCard.vue'
import StartEndNode from './StartEndNode.vue'
import PropertyPanel from './PropertyPanel.vue'
import DbScriptEditModal from '../../../components/DbScriptEditModal.vue'
import ProgramScriptEditModal from '../../../components/ProgramScriptEditModal.vue'
import ApiConfigEditModal from '../../../components/ApiConfigEditModal.vue'
import RedisScriptEditModal from '../../../components/RedisScriptEditModal.vue'
import VarPoolModal from './VarPoolModal.vue'
import FieldMappingModal from '../FieldMappingModal.vue'
import ExecResultPanel from './ExecResultPanel.vue'
import { useNodeOps } from './useNodeOps'
import { PALETTE_ITEMS, NODE_W, validateNodes, buildSaveNodes, buildExecuteNodes, parseFlowNodeList } from './useFlowGraph'
import type { DesignerNode, StartFileConfig, ParamSchema } from './useFlowGraph'
import { useAuthStore } from '../../../stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const flowId = ref<number | string>(route.params.id === 'new' ? 'new' : Number(route.params.id))
const isNewFlow = computed(() => !flowId.value || flowId.value === 'new')
/** 传给 PropertyPanel 的流程 ID（新建未保存时为 null） */
const currentFlowId = computed<number | null>(() => typeof flowId.value === 'number' ? flowId.value : null)
const flowName = ref('')
const flowDesc = ref('')
/** 主线节点列表（真源，图由此派生） */
const mainNodes = ref<DesignerNode[]>([])
const selectedUid = ref<string | null>(null)
const showStartParams = ref(false)
const startInputParams = ref('')
const startFileUploadEnabled = ref(false)
const startTestFile = ref<File | null>(null)
const startExampleEnabled = ref(false)
const startOutputType = ref('JSON')
const startExampleFileId = ref<number | null>(null)
const startExampleFileName = ref('')
/** 暂存的示例文件（保存流程时才真正上传） */
const startExampleStagedFile = ref<File | null>(null)
/** 参数表单 schema（未配置时为 null，随 START config 保存） */
const startParamSchema = ref<ParamSchema | null>(null)
function startFileConfig(): StartFileConfig {
  return {
    fileUploadEnabled: startFileUploadEnabled.value,
    exampleEnabled: startExampleEnabled.value,
    outputType: startOutputType.value,
    exampleFileId: startExampleFileId.value,
    paramSchema: startParamSchema.value,
  }
}
const showNameModal = ref(false)
const editName = ref('')
const editDesc = ref('')
const saving = ref(false)
const executing = ref(false)
/** 执行中请求的取消控制器（点“取消”时中断 HTTP 等待，结果不再渲染） */
const execController = ref<AbortController | null>(null)
// 环境选择（env store 统一管理，跨页面一致）
const envStore = useEnvStore()
const flowEnvId = ref<number | null>(envStore.flowDesignerEnvId)
const envOptions = ref<{ label: string; value: number }[]>([])
async function loadEnvs() {
  try { const res: any = await getEnvActive(); envOptions.value = (res || []).map((e: any) => ({ label: e.name, value: e.id })) } catch { /* ignore */ }
}
function onFlowEnvChange(val: number) {
  flowEnvId.value = val
  envStore.setFlowDesignerEnvId(val)
}
const savingExample = ref(false)
/** 示例输出（从已保存的节点加载） */
const exampleDataMap = ref<Record<string, any>>({})

// ========== 执行结果 ==========
const showExecPanel = ref(false)
const execResult = ref<any>(null)
const isHistory = ref(false) // 历史数据标记（黄色背景）

/** 始终从 execResult 取最后一个成功节点的 data（字段映射的示例数据） */
const lastNodeData = computed(() => {
  const list = execResult.value?.nodeDetailList
  if (!list) return null
  for (let i = list.length - 1; i >= 0; i--) {
    if (list[i].success && list[i].data != null) return list[i].data
  }
  return null
})

/** 节点执行状态映射 Map<nodeCode, NodeResult>，节点卡片响应式读取 */
const nodeExecStatusMap = computed(() => {
  const map: Record<string, any> = {}
  const list = execResult.value?.nodeDetailList
  const flowStopType = execResult.value?.stopType
  if (list) list.forEach((n: any) => { map[n.nodeCode] = { ...n, flowStopType } })
  return map
})

// ========== 图操作（节点增删移、调色板拖放、图重建） ==========
const { vfNodes, vfEdges, rebuildGraph, addNodeToMain, onPaletteDragStart, onDragOver, onDrop } = useNodeOps({
  mainNodes, selectedUid, showStartParams, nodeExecStatusMap, loadOptions,
})

// ========== 选中节点 ==========
const selectedNode = computed<DesignerNode | null>(() => {
  if (!selectedUid.value) return null
  for (const n of mainNodes.value) {
    if (n.uid === selectedUid.value) return n
    for (const bn of n.branchNodes) if (bn.uid === selectedUid.value) return bn
  }
  return null
})

/** 当前选中节点在测试结果中的执行结果 */
const selectedNodeResult = computed(() => {
  if (!execResult.value || !selectedNode.value) return null
  const code = selectedNode.value.nodeCode
  const list = execResult.value.nodeDetailList
  if (list) return list.find((n: any) => n.nodeCode === code) || null
  return execResult.value.nodeResults?.[code] || null
})

const selectedExampleData = computed(() => {
  const code = selectedNode.value?.nodeCode
  return code ? exampleDataMap.value[code] : undefined
})

/** 当前选中节点是否分支节点（分支节点不支持停止条件） */
const selectedIsBranchNode = computed(() => {
  if (!selectedUid.value) return false
  for (const n of mainNodes.value) {
    for (const bn of n.branchNodes) if (bn.uid === selectedUid.value) return true
  }
  return false
})

// ========== 选项列表 ==========
const dbScriptOptions = ref<any[]>([])
const psOptions = ref<any[]>([])
const apiOptions = ref<any[]>([])
const redisScriptOptions = ref<any[]>([])
const flowOptions = ref<any[]>([])
/** 当前流程的内联资源 */
const inlineResources = ref<{ dbScripts: any[]; programScripts: any[]; apiConfigs: any[]; redisScripts: any[] }>({ dbScripts: [], programScripts: [], apiConfigs: [], redisScripts: [] })

async function loadInlineResources() {
  if (isNewFlow.value) return
  try {
    const res: any = await getFlowInlineResources(flowId.value as number)
    inlineResources.value = { dbScripts: res.dbScripts || [], programScripts: res.programScripts || [], apiConfigs: res.apiConfigs || [], redisScripts: res.redisScripts || [] }
  } catch { /* ignore */ }
}

async function loadOptions() {
  try {
    const [db, ps, api, rds, flows] = await Promise.all([
      getDbScriptPage({ pageNum: 1, pageSize: 100 }),
      getPsPage({ pageNum: 1, pageSize: 100 }),
      getApiPage({ pageNum: 1, pageSize: 100 }),
      getRedisScriptPage({ pageNum: 1, pageSize: 200 }),
      getFlowPage({ pageNum: 1, pageSize: 200 }),
    ])
    dbScriptOptions.value = (db as any).list || []
    psOptions.value = (ps as any).list || []
    apiOptions.value = (api as any).list || []
    redisScriptOptions.value = (rds as any).list || []
    // 排除当前流程，避免直接自引用
    flowOptions.value = ((flows as any).list || []).filter((f: any) => f.id !== flowId.value)
  } catch { /* ignore */ }
  loadInlineResources()
}

/** 根据节点类型和配置查找当前选中的脚本（内联编辑用），同时查全局列表和内联资源 */
function getSelectedScript(): any {
  if (!selectedNode.value) return null
  let cfg: any = {}
  try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
  const type = selectedNode.value.nodeType
  if (type === 'REF_DB_SCRIPT' && cfg.dbScriptId) {
    return dbScriptOptions.value.find((s: any) => s.id === cfg.dbScriptId)
      || inlineResources.value.dbScripts.find((s: any) => s.id === cfg.dbScriptId)
      || null
  }
  if (type === 'REF_PROGRAM_SCRIPT' && cfg.programScriptId) {
    return psOptions.value.find((s: any) => s.scriptId === cfg.programScriptId)
      || inlineResources.value.programScripts.find((s: any) => s.scriptId === cfg.programScriptId)
      || null
  }
  return null
}
const hasEditableScript = computed(() => !!getSelectedScript())

const showScriptModal = ref(false)
const editingScript = ref<any>(null)
/** 新建脚本时是否创建为内联资源 */
const scriptCreateInline = ref(false)
/** 当前打开的脚本弹窗类型（由打开时的节点类型决定） */
const scriptModalType = ref<'db' | 'ps'>('db')

function openScriptEdit() {
  if (!selectedNode.value) return
  scriptModalType.value = selectedNode.value.nodeType === 'REF_PROGRAM_SCRIPT' ? 'ps' : 'db'
  editingScript.value = getSelectedScript()
  scriptCreateInline.value = false
  showScriptModal.value = true
}

function openScriptCreate(inline = false) {
  if (!selectedNode.value) return
  scriptModalType.value = selectedNode.value.nodeType === 'REF_PROGRAM_SCRIPT' ? 'ps' : 'db'
  editingScript.value = null
  scriptCreateInline.value = inline
  showScriptModal.value = true
}

/** 脚本保存后：刷新选项；新建时自动把新脚本选中到当前节点 */
function onScriptSaved(saved: any) {
  const wasCreate = editingScript.value == null
  const wasInline = scriptCreateInline.value
  loadOptions()
  if (wasCreate && selectedNode.value) {
    let cfg: any = {}
    try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
    if (scriptModalType.value === 'db' && saved?.id != null) cfg.dbScriptId = saved.id
    if (scriptModalType.value === 'ps' && saved?.scriptId != null) cfg.programScriptId = saved.scriptId
    if (wasInline) cfg.resourceMode = 'inline'
    selectedNode.value.config = JSON.stringify(cfg)
  }
}

const showVarPool = ref(false)
const showFieldMapping = ref(false)

// ========== 接口内联编辑（与脚本内联编辑同构，复用独立页组件） ==========
const showApiModal = ref(false)
const editingApi = ref<any>(null)
/** 新建接口时是否创建为内联资源 */
const apiCreateInline = ref(false)

/** 当前选中节点引用的接口对象（内联编辑用），同时查全局列表和内联资源 */
function getSelectedApi(): any {
  if (!selectedNode.value) return null
  let cfg: any = {}
  try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
  if (selectedNode.value.nodeType === 'REF_API' && cfg.apiConfigId) {
    return apiOptions.value.find((a: any) => a.id === cfg.apiConfigId)
      || inlineResources.value.apiConfigs.find((a: any) => a.id === cfg.apiConfigId)
      || null
  }
  return null
}

function openApiEdit() { editingApi.value = getSelectedApi(); apiCreateInline.value = false; showApiModal.value = true }
function openApiCreate(inline = false) { editingApi.value = null; apiCreateInline.value = inline; showApiModal.value = true }

/** 接口保存后：刷新选项；新建时自动把新接口选中到当前节点 */
function onApiSaved(saved: any) {
  const wasCreate = editingApi.value == null
  const wasInline = apiCreateInline.value
  loadOptions()
  if (wasCreate && saved?.id != null && selectedNode.value) {
    let cfg: any = {}
    try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
    cfg.apiConfigId = saved.id
    if (wasInline) cfg.resourceMode = 'inline'
    selectedNode.value.config = JSON.stringify(cfg)
  }
}

// ========== Redis 配置内联编辑（与接口/脚本内联编辑同构，复用独立页组件）==========
const showRedisModal = ref(false)
const editingRedis = ref<any>(null)
/** 新建 Redis 配置时是否创建为内联资源 */
const redisCreateInline = ref(false)

/** 当前选中 REDIS 节点引用的配置对象，同时查全局列表和内联资源 */
function getSelectedRedis(): any {
  if (!selectedNode.value) return null
  let cfg: any = {}
  try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
  if (selectedNode.value.nodeType === 'REDIS' && cfg.redisConfigId != null) {
    return redisScriptOptions.value.find((s: any) => s.id === cfg.redisConfigId)
      || inlineResources.value.redisScripts.find((s: any) => s.id === cfg.redisConfigId)
      || null
  }
  return null
}

function openRedisEdit() { editingRedis.value = getSelectedRedis(); redisCreateInline.value = false; showRedisModal.value = true }
function openRedisCreate(inline = false) { editingRedis.value = null; redisCreateInline.value = inline; showRedisModal.value = true }

/** Redis 配置保存后：刷新选项；新建时自动把新配置选中到当前节点 */
function onRedisSaved(saved: any) {
  const wasCreate = editingRedis.value == null
  const wasInline = redisCreateInline.value
  loadOptions()
  if (wasCreate && saved?.id != null && selectedNode.value) {
    let cfg: any = {}
    try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
    cfg.redisConfigId = saved.id
    // configCommand 决定图层是否给出分支入口，与 onRedisConfigChange 同口径写入
    cfg.configCommand = saved.command ?? null
    if (wasInline) cfg.resourceMode = 'inline'
    selectedNode.value.config = JSON.stringify(cfg)
    rebuildGraph()
  }
}

// ========== 内联↔引用转换 ==========
/** 内联→全局（promote） */
async function handlePromote(type: string, id: number) {
  try {
    await promoteResource(type, id)
    message.success('已转为全局资源')
    await loadOptions()
  } catch { /* interceptor */ }
}

/** 全局→内联（demote）：复制一份绑定到当前流程，并自动选中 */
async function handleDemote(type: string, id: number) {
  if (isNewFlow.value) { message.warning('请先保存流程再使用内联模式'); return }
  try {
    const res: any = await demoteResource(type, id, flowId.value as number)
    message.success('已转为内联资源')
    await loadOptions()
    // 自动将新内联资源选中到当前节点
    if (res?.id != null && selectedNode.value) {
      let cfg: any = {}
      try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
      if (type === 'db') cfg.dbScriptId = res.id
      else if (type === 'ps') cfg.programScriptId = res.scriptId ?? res.id
      else if (type === 'api') cfg.apiConfigId = res.id
      else if (type === 'redis') cfg.redisConfigId = res.id
      cfg.resourceMode = 'inline'
      selectedNode.value.config = JSON.stringify(cfg)
    }
  } catch { /* interceptor */ }
}

// ========== 节点选择 ==========
function onNodeClick({ node }: any) {
  if (node.id === '__start') {
    selectedUid.value = null
    showStartParams.value = true
    return
  }
  if (node.type === 'biz') {
    selectedUid.value = node.id
    showStartParams.value = false
    loadOptions()
  }
}

/** 根据 nodeCode 选中对应节点（从底部结果表格点击时使用） */
function selectNodeByCode(code: string) {
  for (const n of mainNodes.value) {
    if (n.nodeCode === code) { selectedUid.value = n.uid; showStartParams.value = false; return }
    for (const bn of n.branchNodes) {
      if (bn.nodeCode === code) { selectedUid.value = bn.uid; showStartParams.value = false; return }
    }
  }
}

// ========== 加载 / 保存 / 执行 ==========
async function load() {
  if (isNewFlow.value) {
    flowName.value = (route.query.name as string) || '新流程'
    flowDesc.value = ''
    rebuildGraph(true)
    return
  }
  const res: any = await getFlowDetail(flowId.value as number)
  flowName.value = res.name || ''
  flowDesc.value = res.description || ''
  const nodeRes: any = await getFlowNodes(flowId.value as number)
  const parsed = parseFlowNodeList(nodeRes || [])
  mainNodes.value = parsed.mainNodes
  startInputParams.value = parsed.startInputParams
  startFileUploadEnabled.value = parsed.startFileConfig.fileUploadEnabled
  startExampleEnabled.value = parsed.startFileConfig.exampleEnabled
  startOutputType.value = parsed.startFileConfig.outputType
  startExampleFileId.value = parsed.startFileConfig.exampleFileId
  startParamSchema.value = parsed.startFileConfig.paramSchema ?? null
  // 示例文件名从 file_resource 加载（上传即入库，以此为准）
  startExampleFileName.value = ''
  startExampleStagedFile.value = null
  if (startExampleEnabled.value) {
    try {
      const fi: any = await getFileInfo(flowId.value as number)
      if (fi?.id) {
        startExampleFileId.value = fi.id
        startExampleFileName.value = fi.fileName || ''
      }
    } catch { /* ignore */ }
  }
  exampleDataMap.value = parsed.exampleDataMap
  rebuildGraph(true)
}

async function handleSave() {
  const err = validateNodes(mainNodes.value)
  if (err) { message.warning(err); return }
  saving.value = true
  try {
    if (isNewFlow.value) {
      // 新流程：先创建（此时无示例文件），再上传暂存文件，最后保存节点
      const payload = { name: flowName.value, description: flowDesc.value, nodes: buildSaveNodes(mainNodes.value, startInputParams.value, startFileConfig()) }
      const res: any = await createFlow(payload)
      flowId.value = res.id
      // 用 replace 更新 URL 避免新增一条历史记录
      router.replace({ name: 'FlowDesigner', params: { id: String(res.id) } })
      if (startExampleStagedFile.value) {
        await uploadStagedExample(res.id)
        await saveFlowNodes(res.id, { name: flowName.value, description: flowDesc.value, nodes: buildSaveNodes(mainNodes.value, startInputParams.value, startFileConfig()) })
      }
      message.success('创建成功')
    } else {
      // 已有流程：先上传暂存的示例文件，再保存节点配置（config 中携带新文件ID）
      if (startExampleStagedFile.value) {
        await uploadStagedExample(flowId.value as number)
      }
      const payload = { name: flowName.value, description: flowDesc.value, nodes: buildSaveNodes(mainNodes.value, startInputParams.value, startFileConfig()) }
      await updateFlow(flowId.value as number, { name: flowName.value, description: flowDesc.value })
      await saveFlowNodes(flowId.value as number, payload)
      message.success('保存成功')
    }
  } catch { /* 拦截器处理 */ }
  finally { saving.value = false }
}

/** 上传暂存的示例文件，成功后更新文件ID/名称并清空暂存 */
async function uploadStagedExample(fid: number) {
  const upRes: any = await uploadFile(fid, startExampleStagedFile.value!)
  if (upRes?.id) {
    startExampleFileId.value = upRes.id
    startExampleFileName.value = upRes.fileName || startExampleStagedFile.value!.name
  }
  startExampleStagedFile.value = null
}

async function handleExecute() {
  if (isNewFlow.value) { message.warning('请先保存流程再执行'); return }
  const err = validateNodes(mainNodes.value)
  if (err) { message.warning(err); return }
  executing.value = true
  execResult.value = null
  showExecPanel.value = true
  isHistory.value = false
  execController.value = new AbortController()
  try {
    // 构建执行节点列表
    let nodes = buildExecuteNodes(mainNodes.value, startInputParams.value, startFileConfig())
    // 测试文件：编码为 base64 注入到 START 节点 config 中，供 Groovy 脚本读取
    if (startTestFile.value && nodes.length > 0 && nodes[0].nodeType === 'START') {
      const dataUrl = await new Promise<string>((resolve) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.readAsDataURL(startTestFile.value!)
      })
      const startCfg = JSON.parse(nodes[0].config)
      startCfg._testFileName = startTestFile.value.name
      startCfg._testFileData = dataUrl  // data:xxx;base64,...
      nodes[0] = { ...nodes[0], config: JSON.stringify(startCfg) }
    }
    const payload = { name: flowName.value, description: flowDesc.value, nodes }
    const res: any = await executeFlow(flowId.value as number, payload, flowEnvId.value ?? undefined, execController.value!.signal)
    execResult.value = res
    // 写缓存前裁剪 scriptLogs：日志最多 2000 条（最坏 2MB+），整份写入会撞上 5MB 配额、
    // catch 静默失败导致刷新后执行结果无法恢复；内存里的 execResult 仍保留完整日志
    const { scriptLogs, ...cacheable } = res || {}
    try {
      localStorage.setItem('flow_exec_result_' + flowId.value,
        JSON.stringify({ ...cacheable, scriptLogs: (scriptLogs || []).slice(0, 200) }))
    } catch { /* */ }
  } catch (e: any) {
    // 用户主动取消：不展示任何结果（后端已由 cancelExecute 通知止损）
    if (e?.code === 'ERR_CANCELED') { execResult.value = null; showExecPanel.value = false }
    else {
      const msg = e?.response?.data?.message || e?.message || '执行失败'
      execResult.value = { flowName: flowName.value, executeType: 'MANUAL', success: false, duration: 0, startTime: null, errorMessage: msg, nodeDetailList: [] }
    }
  } finally { executing.value = false; execController.value = null }
}

/** 取消正在执行的流程：中断 HTTP 等待 + 通知后端在下一节点前终止 */
function cancelExecute() {
  execController.value?.abort()
  if (!isNewFlow.value) cancelFlowExecution(flowId.value as number).catch(() => { /* 忽略 */ })
  message.info('已取消执行')
}

async function handleSaveExamples() {
  if (!execResult.value?.success || !execResult.value?.nodeDetailList?.length) return
  savingExample.value = true
  try {
    const list = execResult.value.nodeDetailList
      .filter((n: any) => n.status !== 'STOPPED')
      .map((n: any) => ({
      nodeCode: n.nodeCode,
      exampleOutput: typeof n.data === 'string' ? n.data : JSON.stringify(n.data),
    }))
    await saveFlowExamples(flowId.value as number, list)
    list.forEach((n: any) => {
      try { exampleDataMap.value[n.nodeCode] = JSON.parse(n.exampleOutput) } catch { exampleDataMap.value[n.nodeCode] = n.exampleOutput }
    })
    message.success('结果示例已保存')
  } catch { /* 拦截器处理 */ }
  finally { savingExample.value = false }
}

// ========== 流程名称编辑 ==========
function openNameEdit() {
  editName.value = flowName.value
  editDesc.value = flowDesc.value
  showNameModal.value = true
}

async function saveNameEdit() {
  try {
    if (isNewFlow.value) {
      // 新流程尚未创建，仅更新本地状态，保存时随 createFlow 一起提交
      flowName.value = editName.value
      flowDesc.value = editDesc.value
      showNameModal.value = false
      return
    }
    await updateFlow(flowId.value as number, { name: editName.value, description: editDesc.value })
    flowName.value = editName.value
    flowDesc.value = editDesc.value
    showNameModal.value = false
    message.success('已更新')
  } catch { /* 拦截器处理 */ }
}

/** 跳转到当前流程节点引用的子流程设计器 */
function openReferencedFlow() {
  if (!selectedNode.value) return
  let cfg: any = {}
  try { cfg = JSON.parse(selectedNode.value.config) } catch { cfg = {} }
  if (cfg.flowId) router.push({ name: 'FlowDesigner', params: { id: String(cfg.flowId) } })
}

/** 同路由内跳转（如编辑引用的子流程）时组件实例复用，params 变化需手动重载 */
watch(() => route.params.id, (newId) => {
  if (!newId) return
  const parsedId: number | string = newId === 'new' ? 'new' : Number(newId)
  if (parsedId === flowId.value) return
  flowId.value = parsedId
  selectedUid.value = null
  showStartParams.value = false
  execResult.value = null
  showExecPanel.value = false
  isHistory.value = false
  load(); loadOptions()
  try {
    const cached = localStorage.getItem('flow_exec_result_' + flowId.value)
    if (cached) {
      execResult.value = JSON.parse(cached)
      showExecPanel.value = true
      isHistory.value = true
    }
  } catch { /* ignore */ }
})

onMounted(() => {
  loadEnvs(); load(); loadOptions()
  // 恢复上次执行结果（常驻面板）
  try {
    const cached = localStorage.getItem('flow_exec_result_' + flowId.value)
    if (cached) {
      const parsed = JSON.parse(cached)
      execResult.value = parsed
      showExecPanel.value = true
      isHistory.value = true
    }
  } catch { /* ignore */ }
})
</script>

<template>
  <div style="height: calc(100vh - 80px); display: flex; flex-direction: column">
    <!-- 顶部操作栏 -->
    <NSpace justify="space-between" align="center" style="margin-bottom: 12px">
      <NSpace align="center">
        <h3 style="margin:0; cursor:pointer" @click="openNameEdit" title="点击编辑流程名称">{{ flowName || '未命名流程' }}</h3>
        <NButton size="small" @click="openNameEdit" title="编辑流程名称">✏️</NButton>
        <NTag size="small" type="info" :bordered="false">图形模式</NTag>
      </NSpace>
      <NSpace>
        <NButton type="primary" @click="handleSave" :loading="saving">保存流程</NButton>
        <span style="font-size:13px;color:#666;margin-left:8px">环境：</span>
        <NRadioGroup v-model:value="flowEnvId" size="small" @update-value="onFlowEnvChange">
          <NRadioButton v-for="e in envOptions" :key="e.value" :value="e.value">{{ e.label }}</NRadioButton>
        </NRadioGroup>
        <NButton type="warning" @click="handleExecute" :loading="executing" :disabled="executing || isNewFlow">测试流程</NButton>
        <NButton v-if="executing" type="error" @click="cancelExecute">取消执行</NButton>
        <NButton type="info" @click="showVarPool = true" :disabled="isNewFlow">变量池维护</NButton>
        <NButton type="success" @click="showFieldMapping = true" :disabled="isNewFlow">字段映射</NButton>
        <NButton type="warning" @click="rebuildGraph(true)">重新布局</NButton>
        <NButton @click="router.back()" style="border: 1px solid #d9d9d9">返回</NButton>
      </NSpace>
    </NSpace>

    <!-- 三栏主体 -->
    <div style="display: flex; gap: 12px; flex: 1; overflow: hidden">
      <!-- 左：节点面板 -->
      <div style="width: 150px; background: #fff; border-radius: 8px; padding: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); flex-shrink: 0">
        <h4 style="margin: 0 0 8px; font-size: 14px">可拖拽节点</h4>
        <div style="display: flex; flex-direction: column; gap: 8px">
          <div
            v-for="item in PALETTE_ITEMS"
            :key="item.type"
            draggable="true"
            @dragstart="onPaletteDragStart($event, item)"
            @click="addNodeToMain(item.type)"
            style="padding: 10px 12px; background: #f0f5ff; border: 1px dashed #4080ff; border-radius: 6px; cursor: grab; font-size: 13px; text-align: center; user-select: none"
          >
            {{ item.icon }} {{ item.label }}
          </div>
        </div>
        <div style="margin-top: 16px; color: #999; font-size: 11px; text-align: center; line-height: 1.6">
          拖到画布加入主线<br/>拖到 Redis 节点右侧<br/>加入分支<br/>（点击=追加到主线）
        </div>
      </div>

      <!-- 中：Vue Flow 画布 -->
      <div style="flex: 1; background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); overflow: hidden; position: relative" @drop="onDrop" @dragover="onDragOver">
        <VueFlow
          v-model:nodes="vfNodes"
          v-model:edges="vfEdges"
          :delete-key-code="null"
          :nodes-connectable="false"
          :edges-updatable="false"
          :zoom-on-double-click="false"
          :min-zoom="0.3"
          :max-zoom="1.6"
          fit-view-on-init
          @node-click="onNodeClick"
        >
          <Background :gap="18" pattern-color="#e5e5e5" />
          <Controls position="bottom-left" :show-interactive="false" />
          <template #node-start="nodeProps">
            <StartEndNode :id="nodeProps.id" type="start" :data="{ activeRef: showStartParams ? { value: true } : { value: false } }" />
          </template>
          <template #node-end="nodeProps">
            <StartEndNode :id="nodeProps.id" type="end" :data="nodeProps.data" />
          </template>
          <template #node-biz="nodeProps">
            <FlowNodeCard :id="nodeProps.id" :data="nodeProps.data" />
          </template>
          <template #node-branchHint>
            <div
              :style="{ width: NODE_W + 'px', minHeight: '52px', padding: '10px 12px', background: '#f9f0ff', border: '1.5px dashed #d3adf7', borderRadius: '6px', fontSize: '11px', color: '#9254de', textAlign: 'center', lineHeight: '1.6', boxSizing: 'border-box' }"
            >
              <Handle id="l" type="target" :position="Position.Left" style="background: #9254de" />
              ↳ 分支（缓存未命中时执行）<br/>从左侧拖入节点
            </div>
          </template>
        </VueFlow>
        <div v-if="mainNodes.length === 0" style="position: absolute; top: 45%; left: 0; right: 0; text-align: center; color: #bbb; font-size: 13px; pointer-events: none">
          从左侧拖拽或点击节点添加到画布
        </div>
      </div>

      <!-- 右：属性面板 -->
      <PropertyPanel
        :node="selectedNode"
        :show-start-params="showStartParams"
        :show-exec-panel="showExecPanel"
        :db-script-options="dbScriptOptions"
        :ps-options="psOptions"
        :api-options="apiOptions"
        :redis-script-options="redisScriptOptions"
        :flow-options="flowOptions"
        :inline-resources="inlineResources"
        :node-result="selectedNodeResult"
        :example-data="selectedExampleData"
        :has-editable-script="hasEditableScript"
        :is-branch-node="selectedIsBranchNode"
        :flow-id="currentFlowId"
        :is-admin="authStore.isAdmin"
        v-model:start-input-params="startInputParams"
        v-model:start-file-upload-enabled="startFileUploadEnabled"
        v-model:start-test-file="startTestFile"
        v-model:start-example-enabled="startExampleEnabled"
        v-model:start-output-type="startOutputType"
        v-model:start-example-file-id="startExampleFileId"
        v-model:start-example-file-name="startExampleFileName"
        v-model:start-example-staged-file="startExampleStagedFile"
        v-model:start-param-schema="startParamSchema"
        @edit-script="openScriptEdit"
        @create-script="openScriptCreate"
        @edit-api="openApiEdit"
        @create-api="openApiCreate"
        @edit-redis="openRedisEdit"
        @create-redis="openRedisCreate"
        @edit-flow="openReferencedFlow"
        @refresh-graph="rebuildGraph()"
        @refresh-options="loadOptions"
        @promote="handlePromote"
        @demote="handleDemote"
      />
    </div>

    <!-- 底部：执行结果面板（可拖拽伸缩） -->
    <ExecResultPanel
      v-if="showExecPanel"
      :exec-result="execResult"
      :executing="executing"
      :is-history="isHistory"
      :saving-example="savingExample"
      :flow-id="(flowId as number)"
      @select-node="selectNodeByCode"
      @save-examples="handleSaveExamples"
    />

    <!-- 编辑流程名称 Modal -->
    <NModal v-model:show="showNameModal" preset="card" title="编辑流程信息" style="width: 480px" :mask-closable="false">
      <div style="padding: 8px 0">
        <NFormItem label="流程名称" required><NInput v-model:value="editName" placeholder="请输入流程名称" /></NFormItem>
        <NFormItem label="描述"><NInput v-model:value="editDesc" type="textarea" placeholder="请输入流程描述" /></NFormItem>
        <NSpace justify="end"><NButton @click="showNameModal = false">取消</NButton><NButton type="primary" @click="saveNameEdit">确定</NButton></NSpace>
      </div>
    </NModal>

    <!-- 脚本新建/编辑 Modal（与独立脚本页共用组件，内联/引用表单一致） -->
    <DbScriptEditModal v-if="scriptModalType === 'db'" v-model:show="showScriptModal" :script="editingScript" :flow-id="currentFlowId" :create-inline="scriptCreateInline" @saved="onScriptSaved" />
    <ProgramScriptEditModal v-else v-model:show="showScriptModal" :script="editingScript" :flow-id="currentFlowId" :create-inline="scriptCreateInline" @saved="onScriptSaved" />

    <!-- 接口内联编辑 Modal（与独立接口配置页共用组件） -->
    <ApiConfigEditModal v-model:show="showApiModal" :api="editingApi" :env-id="flowEnvId" :flow-id="(flowId as number)" :create-inline="apiCreateInline" @saved="onApiSaved" />

    <!-- Redis 配置内联编辑 Modal（与独立 Redis 配置页共用组件，业务前缀必选） -->
    <RedisScriptEditModal v-model:show="showRedisModal" :script="editingRedis" :flow-id="currentFlowId" :create-inline="redisCreateInline" @saved="onRedisSaved" />

    <!-- 变量池维护 Modal -->
    <VarPoolModal v-model:show="showVarPool" :flow-id="isNewFlow ? 0 : (flowId as number)" :main-nodes="mainNodes" />
    <FieldMappingModal v-model:show="showFieldMapping" :flow-id="isNewFlow ? 0 : (flowId as number)" :sample-data="lastNodeData" />
  </div>
</template>

<style scoped>
:deep(.vue-flow__handle) {
  width: 8px;
  height: 8px;
}
:deep(.vue-flow__edge-label) {
  font-size: 11px;
}
</style>
