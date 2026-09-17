/**
 * Vue Flow 流程设计器核心：数据模型、图构建（确定性布局）、序列化、校验
 *
 * 数据模型为真源（与旧设计器同构的层级结构），Vue Flow 的 nodes/edges 是派生物，
 * 每次结构变化由 buildGraphElements 重建。与后端的数据契约与旧设计器完全一致。
 */
import type { Ref } from 'vue'

/** 设计器节点（层级模型：REDIS 节点的分支子节点挂在 branchNodes 下） */
export interface DesignerNode {
  uid: string
  /** 数据库 id（加载已保存节点时存在，变量池引用用） */
  realId?: number
  nodeCode: string
  nodeName: string
  nodeType: string
  /** 节点配置 JSON 字符串 */
  config: string
  branchNodes: DesignerNode[]
}

/** 节点卡片上的操作回调（通过 vue-flow node data 传入） */
export interface NodeHandlers {
  onDelete: (uid: string) => void
  onMoveUp: (uid: string) => void
  onMoveDown: (uid: string) => void
}

// ===== 布局常量 =====
export const NODE_W = 220
export const NODE_H = 76
export const GAP_Y = 44
/** 分支列 X 偏移 */
export const BRANCH_X = NODE_W + 110
export const BRANCH_GAP = 32
export const START_END_H = 46

export const NODE_TYPE_LABELS: Record<string, string> = {
  START: '开始', END: '结束',
  DB_QUERY: '数据库查询', GROOVY_SCRIPT: '代码脚本', API_SEND: '接口发送',
  REF_DB_SCRIPT: 'SQL节点', REF_PROGRAM_SCRIPT: '程序节点', REF_API: '接口节点', REDIS: 'Redis节点',
  REF_FLOW: '流程节点',
}

/** 调色板节点（与旧设计器一致） */
export const PALETTE_ITEMS = [
  { type: 'REF_DB_SCRIPT', label: 'SQL节点', icon: '📊' },
  { type: 'REF_PROGRAM_SCRIPT', label: '程序节点', icon: '💻' },
  { type: 'REF_API', label: '接口节点', icon: '🔗' },
  { type: 'REDIS', label: 'Redis节点', icon: '📦' },
  { type: 'REF_FLOW', label: '流程节点', icon: '🔄' },
]

/** 节点类型对应的 NTag 颜色（与旧设计器一致） */
export function tagTypeOf(nodeType: string): 'info' | 'success' | 'warning' {
  if (nodeType === 'REF_DB_SCRIPT' || nodeType === 'REDIS') return 'info'
  if (nodeType === 'REF_PROGRAM_SCRIPT') return 'success'
  return 'warning'
}

/** 流程节点（REF_FLOW）使用紫色系自定义标签色，与蓝色 SQL 节点区分；其余返回 undefined 走 type 默认色 */
export function tagColorOf(nodeType: string): { color: string; textColor: string } | undefined {
  if (nodeType === 'REF_FLOW') return { color: '#f9f0ff', textColor: '#722ed1' }
  return undefined
}

let uidSeq = 0
export function nextUid(prefix: string): string {
  return prefix + '_' + Date.now() + '_' + uidSeq++
}

export function parseConfig(node: DesignerNode): any {
  try { return JSON.parse(node.config || '{}') } catch { return {} }
}

/**
 * 是否可挂分支的节点：REDIS 且所引用配置的命令为 GET&SET。
 * configCommand 是选择配置时冗余写入 config 的命令快照（图层/校验用），后端执行以 DB 配置行为准。
 * 手填模式已废弃：没选配置就无从判定命令，一律不可挂分支。
 */
export function isBranchable(node: DesignerNode): boolean {
  if (node.nodeType !== 'REDIS') return false
  const cfg = parseConfig(node)
  if (cfg.redisConfigId == null) return false
  return String(cfg.configCommand || '').toUpperCase() === 'GET&SET'
}

/** 生成分支节点编码：${parentCode}_b${n}，取最小未占用序号 */
export function genBranchCode(parent: DesignerNode): string {
  const exists = new Set(parent.branchNodes.map((b) => b.nodeCode))
  let i = parent.branchNodes.length + 1
  while (exists.has(parent.nodeCode + '_b' + i)) i++
  return parent.nodeCode + '_b' + i
}

/** 主线节点重新编号 node1..nodeN（与旧设计器新增节点时的行为一致） */
export function renumberMainCodes(mainNodes: DesignerNode[]): void {
  mainNodes.forEach((n, i) => { n.nodeCode = 'node' + (i + 1) })
}

// ===== 图构建 =====

const MAIN_EDGE_STYLE = { stroke: '#bbb', strokeWidth: 1.5 }
const BRANCH_EDGE_STYLE = { stroke: '#9254de', strokeWidth: 1.5, strokeDasharray: '6 4' }

/**
 * 由主线节点列表构建 Vue Flow 的 nodes/edges 与确定性布局。
 * 主线纵向排列（x=0），分支链挂在右侧列（x=BRANCH_X），分支 y 游标防止相邻分支重叠。
 */
export function buildGraphElements(
  mainNodes: DesignerNode[],
  statusMap: Ref<Record<string, any>>,
  selectedUid: Ref<string | null>,
  handlers: NodeHandlers,
) {
  const nodes: any[] = []
  const edges: any[] = []
  const layout = new Map<string, { x: number; y: number }>()

  let mainY = 0
  let branchCursorY = 0

  nodes.push({ id: '__start', type: 'start', position: { x: 0, y: mainY }, data: {} })
  layout.set('__start', { x: 0, y: mainY })
  mainY += START_END_H + GAP_Y

  let prevId = '__start'
  mainNodes.forEach((n, i) => {
    layout.set(n.uid, { x: 0, y: mainY })
    nodes.push({
      id: n.uid, type: 'biz', position: { x: 0, y: mainY },
      data: { node: n, isBranch: false, index: i, count: mainNodes.length, statusMap, selectedUid, handlers },
    })
    edges.push({ id: 'e_main_' + i, source: prevId, target: n.uid, sourceHandle: 'b', targetHandle: 't', style: MAIN_EDGE_STYLE })
    prevId = n.uid

    // REDIS GET&SET 节点的分支链（缓存未命中时执行）；存量分支即使配置不再满足条件也要渲染（避免边悬空）
    if (isBranchable(n) || n.branchNodes.length > 0) {
      const branchY0 = Math.max(mainY, branchCursorY)
      if (n.branchNodes.length === 0) {
        // 空分支：渲染提示占位区，作为拖放目标提示
        nodes.push({
          id: n.uid + '__hint', type: 'branchHint', position: { x: BRANCH_X, y: branchY0 },
          draggable: false, selectable: false, data: { parentUid: n.uid },
        })
        edges.push({
          id: 'e_hint_' + i, source: n.uid, target: n.uid + '__hint',
          sourceHandle: 'branch', targetHandle: 'l',
          label: '未命中', animated: true, style: BRANCH_EDGE_STYLE,
          labelStyle: { fill: '#9254de', fontSize: '11px' }, labelBgStyle: { fill: '#f9f0ff' },
        })
        branchCursorY = branchY0 + NODE_H + BRANCH_GAP
      } else {
        let by = branchY0
        let bPrev = ''
        n.branchNodes.forEach((bn, bi) => {
          layout.set(bn.uid, { x: BRANCH_X, y: by })
          nodes.push({
            id: bn.uid, type: 'biz', position: { x: BRANCH_X, y: by },
            data: { node: bn, isBranch: true, parentUid: n.uid, index: bi, count: n.branchNodes.length, statusMap, selectedUid, handlers },
          })
          if (bi === 0) {
            edges.push({
              id: 'e_branch_' + i + '_0', source: n.uid, target: bn.uid,
              sourceHandle: 'branch', targetHandle: 'l',
              label: '未命中', animated: true, style: BRANCH_EDGE_STYLE,
              labelStyle: { fill: '#9254de', fontSize: '11px' }, labelBgStyle: { fill: '#f9f0ff' },
            })
          } else {
            edges.push({ id: 'e_branch_' + i + '_' + bi, source: bPrev, target: bn.uid, sourceHandle: 'b', targetHandle: 't', style: BRANCH_EDGE_STYLE })
          }
          bPrev = bn.uid
          by += NODE_H + BRANCH_GAP
        })
        branchCursorY = by
      }
    }
    mainY += NODE_H + GAP_Y
  })

  layout.set('__end', { x: 0, y: mainY })
  nodes.push({ id: '__end', type: 'end', position: { x: 0, y: mainY }, data: {} })
  edges.push({ id: 'e_end', source: prevId, target: '__end', sourceHandle: 'b', targetHandle: 't', style: MAIN_EDGE_STYLE })

  return { nodes, edges, layout }
}

// ===== 校验（旧设计器 validateNodes 超集） =====

export function validateNodes(mainNodes: DesignerNode[]): string | null {
  const codes = new Set<string>()
  const all = mainNodes.flatMap((n) => [n, ...n.branchNodes])
  for (const node of all) {
    if (!node.nodeCode || !node.nodeCode.trim()) return `节点「${node.nodeName}」编码不能为空`
    if (codes.has(node.nodeCode)) return `节点编码重复: ${node.nodeCode}`
    codes.add(node.nodeCode)
    const cfg = parseConfig(node)
    if (node.nodeType === 'REF_DB_SCRIPT' && !cfg.dbScriptId) return `节点「${node.nodeName}」未选择数据库脚本`
    if (node.nodeType === 'REF_PROGRAM_SCRIPT' && !cfg.programScriptId) return `节点「${node.nodeName}」未选择程序脚本`
    if (node.nodeType === 'REF_API' && !cfg.apiConfigId) return `节点「${node.nodeName}」未选择接口`
    if (node.nodeType === 'REF_FLOW' && !cfg.flowId) return `节点「${node.nodeName}」未选择流程`
    // REDIS：key/命令/TTL 全部来自所引用的配置行（手填模式已废弃，后端也不再支持）
    if (node.nodeType === 'REDIS' && cfg.redisConfigId == null) return `Redis节点「${node.nodeName}」未选择 Redis 配置`
    // 异常停止：开启后必须填写条件表达式
    if (cfg.stopEnabled && (!cfg.stopCondition || !String(cfg.stopCondition).trim())) return `节点「${node.nodeName}」已开启异常停止但未填写条件表达式`
    // 正常停止：开启后必须填写条件表达式
    if (cfg.normalStopEnabled && (!cfg.normalStopCondition || !String(cfg.normalStopCondition).trim())) return `节点「${node.nodeName}」已开启正常停止但未填写条件表达式`
  }
  for (const n of mainNodes) {
    if (n.branchNodes.length > 0 && !isBranchable(n)) {
      return n.nodeType === 'REDIS'
        ? `Redis节点「${n.nodeName}」配置了分支，只有 GET&SET 命令的 Redis 配置才能挂分支`
        : `节点「${n.nodeName}」不支持分支`
    }
    // GET&SET 的过期时间在配置保存时就已强制要求 >0（后端 REDIS_GETSET_NO_TTL_VAL），此处无需重复校验
  }
  return null
}

// ===== 序列化（与旧设计器字节级一致的载荷） =====

/** 解析流程入参：仅当以 { 或 [ 开头时才尝试按 JSON 解析（避免普通字符串走一次失败的解析），失败当纯字符串，空值返回 undefined */
export function parseFlowParam(raw: string): any {
  const trimmed = (raw || '').trim()
  if (!trimmed) return undefined
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try { return JSON.parse(trimmed) } catch { return trimmed }
  }
  return trimmed
}

/** 参数表单字段定义 */
export interface ParamSchemaField {
  /** 英文字段名（JSON key） */
  name: string
  /** 中文名（表单标签） */
  label: string
  /** 备注（表单提示） */
  remark: string
  /** 字段数据类型 */
  fieldType: 'string' | 'number' | 'boolean'
  /** 是否必填 */
  required: boolean
}

/** 参数表单 schema（存于 START 节点 config.paramSchema，随流程共享流转） */
export interface ParamSchema {
  /** 入参形态：对象（默认）或列表 */
  type: 'object' | 'list'
  fields: ParamSchemaField[]
}

/** 起始节点文件配置 */
export interface StartFileConfig {
  fileUploadEnabled: boolean
  exampleEnabled: boolean
  outputType: string
  exampleFileId: number | null
  /** 参数表单 schema（未配置时为 null） */
  paramSchema?: ParamSchema | null
}

function buildStartConfig(inputParamsObj: any, fileCfg?: StartFileConfig): Record<string, any> {
  const cfg: Record<string, any> = { _input: inputParamsObj }
  if (fileCfg) {
    if (fileCfg.fileUploadEnabled) {
      cfg.fileUploadEnabled = true
      cfg.exampleEnabled = fileCfg.exampleEnabled
      cfg.exampleFileId = fileCfg.exampleFileId
    }
    cfg.outputType = fileCfg.outputType || 'JSON'
    // 参数表单 schema 随 START config 持久化，保存/执行均原样带出
    if (fileCfg.paramSchema && fileCfg.paramSchema.fields && fileCfg.paramSchema.fields.length > 0) {
      cfg.paramSchema = fileCfg.paramSchema
    }
  }
  return cfg
}

/** 构建保存载荷的 nodes（对应旧设计器 handleSave：START 固定 sortOrder=1，分支紧随父节点） */
export function buildSaveNodes(mainNodes: DesignerNode[], startInputParams: string, fileCfg?: StartFileConfig): any[] {
  const inputParamsObj = parseFlowParam(startInputParams)
  return [
    { nodeCode: 'start', nodeName: '开始', nodeType: 'START', sortOrder: 1, config: JSON.stringify(buildStartConfig(inputParamsObj, fileCfg)) },
    ...mainNodes.flatMap((n, i) => {
      const mainNode = { nodeCode: n.nodeCode, nodeName: n.nodeName, nodeType: n.nodeType, sortOrder: i + 2, config: n.config }
      const branchNodeDTOs = n.branchNodes.map((bn, bi) => ({
        nodeCode: bn.nodeCode, nodeName: bn.nodeName, nodeType: bn.nodeType, sortOrder: bi + 1, config: bn.config, branchOfCode: n.nodeCode,
      }))
      return [mainNode, ...branchNodeDTOs]
    }),
  ]
}

/** 构建测试执行载荷的 nodes（对应旧设计器 handleExecute：无入参时不发 START） */
export function buildExecuteNodes(mainNodes: DesignerNode[], startInputParams: string, fileCfg?: StartFileConfig): any[] {
  const inputParamsObj = parseFlowParam(startInputParams)
  const allNodes: any[] = []
  if (inputParamsObj !== undefined || (fileCfg && fileCfg.fileUploadEnabled)) {
    allNodes.push({ nodeCode: 'start', nodeName: '开始', nodeType: 'START', sortOrder: 1, config: JSON.stringify(buildStartConfig(inputParamsObj, fileCfg)) })
  }
  mainNodes.forEach((n) => {
    allNodes.push({ nodeCode: n.nodeCode, nodeName: n.nodeName, nodeType: n.nodeType, sortOrder: allNodes.length + 1, config: n.config })
    n.branchNodes.forEach((bn) => {
      allNodes.push({ nodeCode: bn.nodeCode, nodeName: bn.nodeName, nodeType: bn.nodeType, sortOrder: allNodes.length + 1, config: bn.config, branchOfCode: n.nodeCode })
    })
  })
  return allNodes
}

/** 解析后端节点列表（对应旧设计器 load：branchOf 是父节点数据库 id，映射回父 nodeCode 后挂树） */
export function parseFlowNodeList(list: any[]): { mainNodes: DesignerNode[]; startInputParams: string; startFileConfig: StartFileConfig; exampleDataMap: Record<string, any> } {
  let startInputParams = ''
  const startFileConfig: StartFileConfig = { fileUploadEnabled: false, exampleEnabled: false, outputType: 'JSON', exampleFileId: null }
  const startNode = list.find((n: any) => n.nodeType === 'START')
  if (startNode) {
    try {
      const cfg = JSON.parse(startNode.config || '{}')
      const fp = cfg._input !== undefined ? cfg._input : cfg.flow_param
      if (fp !== undefined) startInputParams = (typeof fp === 'object' && fp !== null) ? JSON.stringify(fp, null, 2) : String(fp)
      startFileConfig.fileUploadEnabled = !!cfg.fileUploadEnabled
      startFileConfig.exampleEnabled = !!cfg.exampleEnabled
      startFileConfig.outputType = cfg.outputType || 'JSON'
      startFileConfig.exampleFileId = cfg.exampleFileId ?? null
      startFileConfig.paramSchema = (cfg.paramSchema && Array.isArray(cfg.paramSchema.fields) && cfg.paramSchema.fields.length > 0)
        ? { type: cfg.paramSchema.type === 'list' ? 'list' : 'object', fields: cfg.paramSchema.fields }
        : null
    } catch { /* ignore */ }
  }
  const exampleDataMap: Record<string, any> = {}
  list.forEach((n: any) => {
    if (n.exampleOutput) {
      try { exampleDataMap[n.nodeCode] = JSON.parse(n.exampleOutput) } catch { exampleDataMap[n.nodeCode] = n.exampleOutput }
    }
  })
  const allNodes = list.filter((n: any) => n.nodeType !== 'START' && n.nodeType !== 'END')
  const mains: any[] = []
  const branchByParent = new Map<string, any[]>()
  allNodes.forEach((n: any) => {
    if (n.branchOf) {
      const parentCode = allNodes.find((p: any) => p.id === n.branchOf)?.nodeCode || ''
      if (!branchByParent.has(parentCode)) branchByParent.set(parentCode, [])
      branchByParent.get(parentCode)!.push(n)
    } else {
      mains.push(n)
    }
  })
  const mainNodes: DesignerNode[] = mains.map((n: any) => ({
    uid: 'db_' + n.id, realId: n.id,
    nodeCode: n.nodeCode, nodeName: n.nodeName, nodeType: n.nodeType, config: n.config || '{}',
    branchNodes: (branchByParent.get(n.nodeCode) || []).map((bn: any, bi: number) => ({
      uid: 'db_b_' + bn.id, realId: bn.id,
      nodeCode: bn.nodeCode || n.nodeCode + '_b' + (bi + 1),
      nodeName: bn.nodeName, nodeType: bn.nodeType, config: bn.config || '{}', branchNodes: [],
    })),
  }))
  return { mainNodes, startInputParams, startFileConfig, exampleDataMap }
}
