<script setup lang="ts">
/**
 * 开始/结束节点（样式与旧设计器一致：开始青色、结束橙色）
 * type=start 时可点击编辑流程入参（由父组件监听 node-click）。
 */
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { NODE_W } from './useFlowGraph'

const props = defineProps<{ id: string; type: string; data: any }>()

const isStart = computed(() => props.type === 'start')
const active = computed<boolean>(() => isStart.value && !!props.data.activeRef?.value)
</script>

<template>
  <div
    class="start-end-node"
    :style="{
      width: NODE_W + 'px',
      background: isStart ? (active ? '#f0f5ff' : '#e6fffb') : '#fff7e6',
      border: isStart ? (active ? '2px solid #4080ff' : '2px solid #13c2c2') : '2px solid #fa8c16',
      color: isStart ? '#08979c' : '#d46b08',
      cursor: isStart ? 'pointer' : 'default',
    }"
  >
    <Handle v-if="!isStart" id="t" type="target" :position="Position.Top" />
    <Handle v-if="isStart" id="b" type="source" :position="Position.Bottom" />
    {{ isStart ? '🚀 开始节点' : '🏁 结束节点' }}
  </div>
</template>

<style scoped>
.start-end-node {
  padding: 12px 14px;
  border-radius: 8px;
  text-align: center;
  font-weight: 600;
  font-size: 14px;
  box-sizing: border-box;
}
</style>
