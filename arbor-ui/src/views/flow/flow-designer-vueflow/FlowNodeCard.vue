<script setup lang="ts">
/**
 * Vue Flow 自定义业务节点卡片
 * 显示类型标签/名称/编码/执行状态，提供 ↑↓ 排序与删除操作；
 * REDIS 节点带右侧分支连接点（branch source handle）。
 */
import { computed, unref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { NTag, NButton, NPopconfirm } from 'naive-ui'
import { formatDuration } from '../../../utils/format'
import { NODE_TYPE_LABELS, NODE_W, tagTypeOf, tagColorOf, isBranchable } from './useFlowGraph'
import type { DesignerNode } from './useFlowGraph'

const props = defineProps<{ id: string; data: any }>()

const node = computed<DesignerNode>(() => props.data.node)
const isBranch = computed<boolean>(() => !!props.data.isBranch)
// data 经 Vue Flow 内部 reactive 代理后嵌套 ref 会被自动解包，用 unref 兼容 ref/已解包两种形态
const selected = computed<boolean>(() => unref(props.data.selectedUid) === props.id)
const status = computed<any>(() => {
  const m = unref(props.data.statusMap)
  return m ? m[node.value.nodeCode] : undefined
})
/** 分支连接点：GET&SET 配置可挂分支；存量分支节点仍在时保留连接点（避免边悬空） */
const showBranchHandle = computed(() => !isBranch.value && (isBranchable(node.value) || node.value.branchNodes.length > 0))
/** 是否开启了停止条件（角标提示） */
const hasStopCondition = computed(() => {
  try { return !!JSON.parse(node.value.config).stopEnabled } catch { return false }
})
/** 是否开启了正常停止条件 */
const hasNormalStop = computed(() => {
  try { return !!JSON.parse(node.value.config).normalStopEnabled } catch { return false }
})
/** 停止类型：从执行结果获取（仅执行后有效） */
const stopType = computed(() => {
  return status.value?.flowStopType || 'ABNORMAL'
})
/** 节点因流程停止而未执行 */
const isStopped = computed(() => status.value?.status === 'STOPPED')

const borderStyle = computed(() => {
  if (selected.value) return '2px solid #4080ff'
  if (status.value) {
    if (isStopped.value) return stopType.value === 'NORMAL' ? '1.5px solid #2080f0' : '1.5px solid #f0a020'
    return status.value.success ? '1.5px solid #18a058' : '1.5px solid #d03050'
  }
  return isBranch.value ? '1px solid #d3adf7' : '1px solid #e8e8e8'
})
</script>

<template>
  <div
    class="flow-node-card"
    :style="{
      width: NODE_W + 'px',
      border: borderStyle,
      background: selected ? '#f0f5ff' : (isBranch ? '#fdfaff' : '#fafafa'),
    }"
  >
    <Handle id="t" type="target" :position="Position.Top" />
    <Handle id="b" type="source" :position="Position.Bottom" />
    <Handle v-if="isBranch" id="l" type="target" :position="Position.Left" />
    <Handle v-if="showBranchHandle" id="branch" type="source" :position="Position.Right" class="branch-handle" />

    <div style="display: flex; align-items: center; gap: 4px">
      <NTag size="tiny" :type="tagTypeOf(node.nodeType)" :color="tagColorOf(node.nodeType)" :bordered="false">
        {{ NODE_TYPE_LABELS[node.nodeType] || node.nodeType }}
      </NTag>
      <strong style="font-size: 13px; flex: 1; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ node.nodeName }}</strong>
      <span v-if="hasStopCondition && !hasNormalStop" title="已开启异常停止" style="font-size: 10px; color: #f0a020">🛑</span>
      <span v-if="hasNormalStop && !hasStopCondition" title="已开启正常停止" style="font-size: 10px; color: #2080f0">🟢</span>
      <span v-if="hasStopCondition && hasNormalStop" title="已开启异常+正常停止" style="font-size: 10px; color: #f0a020">🛑</span>
      <NTag v-if="status" size="tiny" :type="isStopped ? (stopType === 'NORMAL' ? 'info' : 'warning') : (status.success ? 'success' : 'error')" :bordered="false">
        {{ isStopped ? (stopType === 'NORMAL' ? '✓' : '⏸') : (status.success ? '✓' : '✕') }}
      </NTag>
    </div>
    <div style="margin-top: 4px; display: flex; justify-content: space-between; align-items: center">
      <span style="color: #999; font-size: 11px">{{ node.nodeCode }}</span>
      <span v-if="status && !isStopped" style="color: #999; font-size: 10px">{{ formatDuration(status.duration) }}</span>
      <span class="nodrag" style="display: flex; gap: 8px">
        <NButton size="small" text :disabled="data.index === 0" @click.stop="data.handlers.onMoveUp(id)" style="font-size:16px">↑</NButton>
        <NButton size="small" text :disabled="data.index >= data.count - 1" @click.stop="data.handlers.onMoveDown(id)" style="font-size:16px">↓</NButton>
        <NPopconfirm @positive-click="() => data.handlers.onDelete(id)">
          <template #trigger><NButton size="small" type="error" text @click.stop style="font-size:16px">×</NButton></template>
          {{ isBranch ? '删除此分支节点？' : '删除此节点？' }}
        </NPopconfirm>
      </span>
    </div>
  </div>
</template>

<style scoped>
.flow-node-card {
  padding: 12px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: border 0.2s, background 0.2s;
  box-sizing: border-box;
}
.branch-handle {
  background: #9254de;
}
</style>
