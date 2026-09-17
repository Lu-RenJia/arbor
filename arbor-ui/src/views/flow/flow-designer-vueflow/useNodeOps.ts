/**
 * 图交互操作 composable：Vue Flow 元素重建、节点增删移、调色板拖放落点判定。
 * 数据契约相关的纯函数在 useFlowGraph.ts；本文件负责图交互状态与行为。
 */
import { ref, nextTick, type Ref } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import { useMessage } from 'naive-ui'
import {
  NODE_TYPE_LABELS, NODE_W, NODE_H, BRANCH_X,
  buildGraphElements, nextUid, genBranchCode, renumberMainCodes, isBranchable,
} from './useFlowGraph'
import type { DesignerNode } from './useFlowGraph'

export function useNodeOps(opts: {
  /** 主线节点列表（真源） */
  mainNodes: Ref<DesignerNode[]>
  selectedUid: Ref<string | null>
  showStartParams: Ref<boolean>
  /** 节点执行状态映射，节点卡片响应式读取 */
  nodeExecStatusMap: Ref<Record<string, any>>
  /** 新增节点后刷新资源选项 */
  loadOptions: () => void
}) {
  const { mainNodes, selectedUid, showStartParams, nodeExecStatusMap, loadOptions } = opts
  const { fitView, screenToFlowCoordinate } = useVueFlow()
  const message = useMessage()

  // ========== Vue Flow 图状态（派生） ==========
  const vfNodes = ref<any[]>([])
  const vfEdges = ref<any[]>([])
  let layoutMap = new Map<string, { x: number; y: number }>()

  // ========== 图重建 ==========
  const nodeHandlers = {
    onDelete: handleNodeDelete,
    onMoveUp: (uid: string) => handleNodeMove(uid, -1),
    onMoveDown: (uid: string) => handleNodeMove(uid, 1),
  }

  function rebuildGraph(refit = false) {
    const { nodes, edges, layout } = buildGraphElements(mainNodes.value, nodeExecStatusMap, selectedUid, nodeHandlers)
    vfNodes.value = nodes
    vfEdges.value = edges
    layoutMap = layout
    if (refit) nextTick(() => fitView({ padding: 0.2 }))
  }

  /** 定位节点：返回所在列表与下标 */
  function findNodeLoc(uid: string): { list: DesignerNode[]; index: number } | null {
    const mi = mainNodes.value.findIndex((n) => n.uid === uid)
    if (mi >= 0) return { list: mainNodes.value, index: mi }
    for (const n of mainNodes.value) {
      const bi = n.branchNodes.findIndex((bn) => bn.uid === uid)
      if (bi >= 0) return { list: n.branchNodes, index: bi }
    }
    return null
  }

  function handleNodeDelete(uid: string) {
    const loc = findNodeLoc(uid)
    if (!loc) return
    const removed = loc.list.splice(loc.index, 1)[0]
    const removedUids = new Set([removed.uid, ...removed.branchNodes.map((b) => b.uid)])
    if (selectedUid.value && removedUids.has(selectedUid.value)) selectedUid.value = null
    rebuildGraph()
  }

  function handleNodeMove(uid: string, dir: -1 | 1) {
    const loc = findNodeLoc(uid)
    if (!loc) return
    const to = loc.index + dir
    if (to < 0 || to >= loc.list.length) return
    const tmp = loc.list[loc.index]
    loc.list[loc.index] = loc.list[to]
    loc.list[to] = tmp
    rebuildGraph()
  }

  // ========== 节点新增（调色板点击 / 拖放） ==========
  function createNode(type: string): DesignerNode {
    return {
      uid: nextUid('new'),
      nodeCode: '',
      nodeName: NODE_TYPE_LABELS[type] || type,
      nodeType: type,
      config: '{}',
      branchNodes: [],
    }
  }

  /** 追加到主线（默认末尾，可指定插入位置） */
  function addNodeToMain(type: string, insertIndex?: number) {
    const node = createNode(type)
    const idx = insertIndex ?? mainNodes.value.length
    mainNodes.value.splice(idx, 0, node)
    // 新增主线节点时统一重新编号，避免 nodeCode 重复
    renumberMainCodes(mainNodes.value)
    selectedUid.value = node.uid
    showStartParams.value = false
    rebuildGraph()
    loadOptions()
  }

  /** 追加到指定 REDIS GET 节点的分支链 */
  function addNodeToBranch(parent: DesignerNode, type: string) {
    const node = createNode(type)
    node.nodeCode = genBranchCode(parent)
    parent.branchNodes.push(node)
    selectedUid.value = node.uid
    showStartParams.value = false
    rebuildGraph()
    loadOptions()
  }

  function onPaletteDragStart(e: DragEvent, item: any) {
    e.dataTransfer?.setData('application/arbor-node-type', item.type)
    if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
  }

  function onDragOver(e: DragEvent) {
    e.preventDefault()
    if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  }

  /**
   * 画布拖放：
   * 1. 落在主线列某个可挂分支（GET&SET）的 REDIS 节点卡片上 → 加入其分支
   * 2. 落在分支列（x 接近 BRANCH_X）→ 加入 y 最近的可挂分支 REDIS 节点分支
   * 3. 其他 → 按 y 插入主线对应位置
   */
  function onDrop(e: DragEvent) {
    const type = e.dataTransfer?.getData('application/arbor-node-type')
    if (!type) return
    const pos = screenToFlowCoordinate({ x: e.clientX, y: e.clientY })
    const branchableNodes = mainNodes.value.filter(isBranchable)

    // 1. 直接落在主线可挂分支的 REDIS 卡片上
    if (pos.x >= -NODE_W / 2 && pos.x <= NODE_W * 1.5) {
      for (const n of branchableNodes) {
        const ly = layoutMap.get(n.uid)
        if (ly && pos.y >= ly.y - 10 && pos.y <= ly.y + NODE_H + 10 && pos.x <= NODE_W + 40) {
          addNodeToBranch(n, type)
          return
        }
      }
    }

    // 2. 分支列
    if (pos.x >= BRANCH_X - 60) {
      if (branchableNodes.length === 0) {
        message.warning('请先添加 Redis 节点并设为 GET&SET（手填操作或选择 GET&SET 命令的 Redis 配置），再配置分支')
        return
      }
      let best: DesignerNode = branchableNodes[0]
      let bestDist = Number.MAX_SAFE_INTEGER
      for (const n of branchableNodes) {
        const ly = layoutMap.get(n.uid)
        const lastBranch = n.branchNodes[n.branchNodes.length - 1]
        const zoneY = lastBranch ? (layoutMap.get(lastBranch.uid)?.y ?? ly?.y ?? 0) : (ly?.y ?? 0)
        const dist = Math.abs(zoneY - pos.y)
        if (dist < bestDist) { bestDist = dist; best = n }
      }
      addNodeToBranch(best, type)
      return
    }

    // 3. 主线插入：按 y 找插入位
    let idx = mainNodes.value.length
    for (let i = 0; i < mainNodes.value.length; i++) {
      const ly = layoutMap.get(mainNodes.value[i].uid)
      if (ly && pos.y < ly.y + NODE_H / 2) { idx = i; break }
    }
    addNodeToMain(type, idx)
  }

  return {
    vfNodes, vfEdges,
    rebuildGraph,
    addNodeToMain, addNodeToBranch,
    onPaletteDragStart, onDragOver, onDrop,
  }
}
