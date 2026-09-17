<script setup lang="ts">
/**
 * 引用追踪抽屉：展示资源被引用的有向图（DAG），使用 Vue Flow 渲染
 * 调用方式：通过 ref 调用 open(type, id, name)
 * 双击节点可跳转到对应资源的编辑/列表页面
 */
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NDrawer, NDrawerContent, NSpin, NEmpty, NTag } from 'naive-ui'
import { VueFlow, useVueFlow, MarkerType } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { getResourceTrace } from '../api/resourceTrace'
import type { ResourceTraceVO } from '../api/resourceTrace'

const router = useRouter()
const visible = ref(false)
const loading = ref(false)
const traceData = ref<ResourceTraceVO | null>(null)
const empty = ref(false)

const { fitView, onPaneReady } = useVueFlow({ id: 'trace-flow' })

// pane 就绪标记：VueFlow 容器真正完成初始化后才允许 fitView
let paneReady = false
onPaneReady(() => { paneReady = true })

// Vue Flow 数据
const vfNodes = ref<any[]>([])
const vfEdges = ref<any[]>([])

// 资源类型 → 颜色/标签映射
const TYPE_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  global_param: { color: '#08979c', bg: '#e6fffb', label: '全局参数' },
  datasource: { color: '#1d39c4', bg: '#f0f5ff', label: '数据源' },
  db_script: { color: '#531dab', bg: '#f9f0ff', label: '数据库脚本' },
  program_script: { color: '#c41d7f', bg: '#fff0f6', label: '程序脚本' },
  api_config: { color: '#d46b08', bg: '#fff7e6', label: '接口配置' },
  redis_datasource: { color: '#cf1322', bg: '#fff1f0', label: 'Redis数据源' },
  redis_script: { color: '#cf1322', bg: '#fff1f0', label: 'Redis脚本' },
  flow: { color: '#389e0d', bg: '#f6ffed', label: '流程' },
  flow_node: { color: '#595959', bg: '#fafafa', label: '流程节点' },
  application: { color: '#2080f0', bg: '#e8f4ff', label: '应用' },
}

/** 资源类型 → 跳转路由（flow 跳设计器，其余跳列表页） */
const TYPE_ROUTES: Record<string, (id: string) => string> = {
  flow: (id) => `/flow/designer/${id}`,
  global_param: () => '/service/param',
  datasource: () => '/service/datasource',
  db_script: () => '/service/db-script',
  program_script: () => '/service/program-script',
  api_config: () => '/service/api-config',
  redis_datasource: () => '/service/redis-datasource',
  redis_script: () => '/service/redis-script',
  flow_node: () => '/flow/list',
  application: () => '/app/my',
}

function getTypeConf(type: string) {
  return TYPE_CONFIG[type] || { color: '#595959', bg: '#fafafa', label: type }
}

/** 打开抽屉并加载追踪数据 */
async function open(type: string, id: number | string, _name?: string) {
  visible.value = true
  loading.value = true
  empty.value = false
  traceData.value = null
  vfNodes.value = []
  vfEdges.value = []

  try {
    const res: any = await getResourceTrace(type, id)
    traceData.value = res
    if (!res || !res.nodes || res.nodes.length === 0) {
      empty.value = true
      return
    }
    buildGraph(res)
    await nextTick()
    // 等待 Drawer 动画结束后再 fitView（NDrawer 默认动画 ~300ms），确保容器尺寸已确定
    scheduleFitView()
  } catch {
    empty.value = true
  } finally {
    loading.value = false
  }
}

/** 延迟 fitView：等 Drawer 过渡动画完成、容器拿到真实宽高后再居中视口 */
function scheduleFitView() {
  const doFit = () => fitView({ padding: 0.25, duration: 200 })
  if (paneReady) {
    setTimeout(doFit, 320)
  } else {
    // pane 尚未就绪时轮询等待（最多 2s）
    let tries = 0
    const timer = setInterval(() => {
      tries++
      if (paneReady || tries > 20) {
        clearInterval(timer)
        if (paneReady) doFit()
      }
    }, 100)
  }
}

/** 将后端返回的 nodes/edges 转换为 Vue Flow 布局（按 level 分层，同层水平排列） */
function buildGraph(data: ResourceTraceVO) {
  const NODE_W = 200
  const NODE_H = 56
  const GAP_X = 60
  const GAP_Y = 90

  // 按 level 分组
  const levelMap = new Map<number, typeof data.nodes>()
  data.nodes.forEach((n) => {
    const list = levelMap.get(n.level) || []
    list.push(n)
    levelMap.set(n.level, list)
  })

  const nodes: any[] = []
  const edges: any[] = []

  // 计算每层节点位置：居中排列
  const maxCount = Math.max(...[...levelMap.values()].map((l) => l.length))
  const totalWidth = maxCount * (NODE_W + GAP_X) - GAP_X

  levelMap.forEach((levelNodes, level) => {
    const levelWidth = levelNodes.length * (NODE_W + GAP_X) - GAP_X
    const offsetX = (totalWidth - levelWidth) / 2
    levelNodes.forEach((n, i) => {
      const conf = getTypeConf(n.type)
      nodes.push({
        id: n.id,
        position: { x: offsetX + i * (NODE_W + GAP_X), y: level * (NODE_H + GAP_Y) },
        type: 'traceNode',
        data: { label: n.label, type: n.type, color: conf.color, bg: conf.bg, typeLabel: conf.label },
        style: { width: NODE_W + 'px' },
      })
    })
  })

  data.edges.forEach((e, i) => {
    edges.push({
      id: 'te_' + i,
      source: e.source,
      target: e.target,
      type: 'smoothstep',
      animated: true,
      style: { stroke: '#91d5ff', strokeWidth: 1.5 },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#91d5ff', width: 16, height: 16 },
    })
  })

  vfNodes.value = nodes
  vfEdges.value = edges
}

/** 双击节点跳转：flow→流程设计器，其余→对应列表页 */
function onNodeDoubleClick({ node }: any) {
  const nodeId: string = node.id || ''
  const sepIdx = nodeId.indexOf(':')
  if (sepIdx < 0) return
  const nodeType = nodeId.substring(0, sepIdx)
  const resourceId = nodeId.substring(sepIdx + 1)
  const routeFn = TYPE_ROUTES[nodeType]
  if (routeFn) {
    visible.value = false
    router.push(routeFn(resourceId))
  }
}

defineExpose({ open })
</script>

<template>
  <NDrawer v-model:show="visible" :width="680" placement="right">
    <NDrawerContent :title="traceData ? `引用追踪：${traceData.rootName}` : '引用追踪'" closable>
      <div class="trace-container">
        <!-- VueFlow 始终挂载，避免条件渲染导致实例销毁/重建引发的渲染异常 -->
        <div class="trace-graph-wrap">
          <VueFlow
            id="trace-flow"
            v-model:nodes="vfNodes"
            v-model:edges="vfEdges"
            :nodes-draggable="true"
            :nodes-connectable="false"
            :edges-updatable="false"
            :zoom-on-double-click="false"
            :zoom-on-scroll="true"
            :pan-on-scroll="false"
            :zoom-on-pinch="true"
            :min-zoom="0.2"
            :max-zoom="3"
            :default-viewport="{ x: 0, y: 0, zoom: 1 }"
            @node-double-click="onNodeDoubleClick"
          >
            <Background :gap="16" pattern-color="#eee" />
            <Controls position="bottom-left" :show-interactive="false" />
            <template #node-traceNode="nodeProps">
              <div
                class="trace-node"
                :style="{
                  borderColor: nodeProps.data.color,
                  background: nodeProps.data.bg,
                }"
                :title="'双击跳转到' + nodeProps.data.typeLabel + '页面'"
              >
                <NTag :bordered="false" size="small" :style="{ color: nodeProps.data.color, background: 'transparent', fontWeight: 600 }">
                  {{ nodeProps.data.typeLabel }}
                </NTag>
                <div class="trace-node-label" :style="{ color: nodeProps.data.color }">
                  {{ nodeProps.data.label }}
                </div>
              </div>
            </template>
          </VueFlow>
        </div>

        <!-- 加载/空状态覆盖层（不销毁底层画布） -->
        <div v-if="loading" class="trace-overlay">
          <NSpin size="large" />
        </div>
        <div v-else-if="empty" class="trace-overlay">
          <NEmpty description="该资源暂未被任何其他资源引用" />
        </div>
      </div>

      <template #footer>
        <span style="font-size: 12px; color: #999">滚轮缩放 · 拖拽移动节点 · 双击节点跳转对应页面</span>
      </template>
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.trace-container {
  position: relative;
  width: 100%;
  height: calc(100vh - 140px);
}

.trace-graph-wrap {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.trace-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 8px;
  z-index: 10;
}

.trace-node {
  padding: 10px 14px;
  border-radius: 8px;
  border: 2px solid;
  text-align: center;
  min-width: 160px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
  cursor: pointer;
}

.trace-node-label {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}
</style>
