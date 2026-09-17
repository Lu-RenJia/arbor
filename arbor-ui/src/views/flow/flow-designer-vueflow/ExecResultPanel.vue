<script setup lang="ts">
/**
 * 底部执行结果面板：可拖拽伸缩 / 收起展开 / 概览·日志·JSON·映射四视图。
 * 点击表格行通过 emit 定位节点；保存结果示例由父组件回写 exampleDataMap。
 */
import { ref, computed, watch, h } from 'vue'
import { NButton, NSpace, NTag, NRadioGroup, NRadioButton, NDataTable } from 'naive-ui'
import { formatDateTime, formatDuration } from '../../../utils/format'
import { downloadFileById } from '../../../api/file'
import MappedResultTable from '../../../components/MappedResultTable.vue'
import LogConsole from '../../../components/LogConsole.vue'
import { NODE_TYPE_LABELS } from './useFlowGraph'

const props = defineProps<{
  execResult: any
  executing: boolean
  /** 是否为 localStorage 恢复的历史数据（高亮提示） */
  isHistory: boolean
  savingExample: boolean
  flowId: number
}>()

const emit = defineEmits<{ (e: 'select-node', nodeCode: string): void; (e: 'save-examples'): void }>()

const execPanelCollapsed = ref(false)
const execViewMode = ref<'table' | 'log' | 'json' | 'mapped'>('table')
/** 最后一个节点的原始数据，传给 MappedResultTable */
const mappedRawData = ref<any>(null)

const execPanelHeight = ref(300)
const execPanelMin = 120
const execPanelMax = 600
const isDragging = ref(false)
let dragStartY = 0
let dragStartH = 0

function onExecDragStart(e: MouseEvent) {
  isDragging.value = true
  dragStartY = e.clientY
  dragStartH = execPanelHeight.value
  document.addEventListener('mousemove', onExecDragMove)
  document.addEventListener('mouseup', onExecDragEnd)
}
function onExecDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  const delta = dragStartY - e.clientY
  execPanelHeight.value = Math.min(execPanelMax, Math.max(execPanelMin, dragStartH + delta))
}
function onExecDragEnd() {
  isDragging.value = false
  document.removeEventListener('mousemove', onExecDragMove)
  document.removeEventListener('mouseup', onExecDragEnd)
}

/** 新一轮执行结果到来时重置视图状态 */
watch(() => props.execResult, () => {
  execViewMode.value = 'table'
  execPanelCollapsed.value = false
  mappedRawData.value = null
})

/** 隐藏 _testFileBytes 的 execResult（base64 数据无展示意义且体积巨大） */
const sanitizedExecResult = computed(() => {
  const r = props.execResult
  if (!r) return null
  const copy = JSON.parse(JSON.stringify(r))
  if (copy.nodeDetailList) {
    for (const n of copy.nodeDetailList) {
      if (n.data && n.data._testFileBytes) delete n.data._testFileBytes
    }
  }
  return copy
})

function fmtData(data: any): string {
  if (data === null || data === undefined) return '-'
  if (typeof data === 'string') return data.length > 300 ? data.slice(0, 300) + '…' : data
  return JSON.stringify(data, null, 2)
}

const execColumns = computed(() => {
  const list = props.execResult?.nodeDetailList
  if (!list || list.length === 0) return []
  return [
    { title: '#', key: '_idx', width: 36 },
    { title: '编码', key: 'nodeCode', width: 80 },
    { title: '名称', key: 'nodeName', width: 90 },
    { title: '类型', key: 'nodeType', width: 90, render: (r: any) => NODE_TYPE_LABELS[r.nodeType] || r.nodeType },
    { title: '状态', key: 'success', width: 72, render: (r: any) => r.status === 'STOPPED'
        ? h(NTag, { size: 'small', type: props.execResult?.stopType === 'NORMAL' ? 'info' : 'warning' }, { default: () => props.execResult?.stopType === 'NORMAL' ? '正常停止' : '流程停止' })
        : (r.success ? h(NTag, { size: 'small', type: 'success' }, { default: () => '成功' }) : h(NTag, { size: 'small', type: 'error' }, { default: () => '失败' })) },
    { title: '耗时', key: 'duration', width: 72, render: (r: any) => r.status === 'STOPPED' ? '-' : formatDuration(r.duration) },
    { title: '结果', key: 'data', ellipsis: { tooltip: true }, render: (r: any) => r.status === 'STOPPED' ? '-' : (r.success ? fmtData(r.data) : (r.errorMessage || '-')) },
  ]
})

const execData = computed(() => {
  const list = props.execResult?.nodeDetailList
  if (!list) return []
  return list.map((n: any, i: number) => ({ ...n, _idx: i + 1 }))
})

function openOutputFile() {
  if (props.execResult?.outputFile) {
    downloadFileById(props.execResult.outputFile.fileId)
  }
}

/** 加载映射原始数据（取最后一个有输出的节点） */
function loadMappedRawData() {
  mappedRawData.value = null
  const list = props.execResult?.nodeDetailList
  if (!list || list.length === 0) { mappedRawData.value = undefined; return }
  for (let i = list.length - 1; i >= 0; i--) {
    const node = list[i]
    if (node.success && node.data != null) {
      mappedRawData.value = node.data
      return
    }
  }
  mappedRawData.value = undefined
}

function onExecViewChange(val: string) {
  execViewMode.value = val as any
  if (val === 'mapped') loadMappedRawData()
}
</script>

<template>
  <div :style="{ height: execPanelHeight + 'px', flexShrink: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }">
    <div
      @mousedown="onExecDragStart"
      :style="{ height: '6px', background: isDragging ? '#4080ff' : '#e8e8e8', cursor: 'ns-resize', flexShrink: 0, transition: isDragging ? 'none' : 'background 0.2s' }"
    />
    <div style="flex:1; background:#fff; border-top:1px solid #e8e8e8; overflow:auto; padding:8px 16px; display:flex; flex-direction:column">
      <NSpace justify="space-between" align="center" style="margin-bottom: 6px">
        <NSpace align="center" :size="12">
          <span :style="{fontWeight:'600', fontSize:'13px', background: isHistory ? '#fffbe6' : 'transparent', padding: isHistory ? '2px 6px' : '0', borderRadius:'3px'}">测试结果</span>
          <template v-if="execResult">
            <span style="font-size:12px"><strong>流程：</strong>{{ execResult.flowName }}</span>
            <NTag :type="execResult.status === 'STOPPED' ? (execResult.stopType === 'NORMAL' ? 'info' : 'warning') : (execResult.success ? 'success' : 'error')" size="tiny">{{ execResult.status === 'STOPPED' ? (execResult.stopType === 'NORMAL' ? '正常停止' : '流程停止') : (execResult.success ? '成功' : '失败') }}</NTag>
            <span style="font-size:12px"><strong>总耗时：</strong>{{ formatDuration(execResult.duration) }}</span>
            <span v-if="execResult.startTime" style="font-size:12px"><strong>开始：</strong>{{ formatDateTime(execResult.startTime) }}</span>
          </template>
        </NSpace>
        <NSpace>
          <NButton v-if="execResult?.outputFile" size="tiny" type="info" @click="openOutputFile">📥 {{ execResult.outputFile.fileName }}{{ execResult.outputFile.sizeText ? ` (${execResult.outputFile.sizeText})` : '' }}</NButton>
          <NButton v-if="execResult && execResult.success" size="tiny" type="primary" @click="emit('save-examples')" :loading="savingExample">💾 保存结果示例</NButton>
          <NRadioGroup v-if="execResult" :value="execViewMode" size="small" @update:value="onExecViewChange">
            <NRadioButton value="table">概览</NRadioButton>
            <NRadioButton value="log">日志</NRadioButton>
            <NRadioButton value="json">JSON</NRadioButton>
            <NRadioButton value="mapped">映射</NRadioButton>
          </NRadioGroup>
          <NButton size="tiny" @click="execPanelCollapsed = !execPanelCollapsed">{{ execPanelCollapsed ? '展开' : '收起' }}</NButton>
        </NSpace>
      </NSpace>

      <div v-if="execPanelCollapsed" style="text-align:center; padding:8px; color:#999; flex-shrink:0; font-size:12px">执行结果已收起，点击"展开"查看</div>
      <div v-else-if="executing" style="text-align:center; padding: 30px; color: #999; flex:1">执行中…</div>
      <template v-else-if="execResult && !execPanelCollapsed">
        <div v-if="execResult.status === 'STOPPED' && execResult.stopMessage"
          :style="{
            padding: '6px 10px',
            background: execResult.stopType === 'NORMAL' ? '#e8f4fd' : '#fffbe6',
            border: execResult.stopType === 'NORMAL' ? '1px solid #91caff' : '1px solid #ffe58f',
            borderRadius: '4px',
            marginBottom: '6px',
            fontSize: '12px',
            color: execResult.stopType === 'NORMAL' ? '#1677ff' : '#d48806',
            flexShrink: '0'
          }">
          {{ execResult.stopType === 'NORMAL' ? '✅' : '⏸' }} {{ execResult.stopType === 'NORMAL' ? '正常停止原因' : '停止原因' }}：{{ execResult.stopMessage }}
        </div>
        <div v-if="execResult.errorMessage" style="padding:6px 10px; background:#fff2f0; border:1px solid #ffccc7; border-radius:4px; margin-bottom:6px; font-size:12px; color:#cf1322; flex-shrink:0">{{ execResult.errorMessage }}</div>
        <NDataTable
          v-if="execViewMode === 'table'"
          :columns="execColumns"
          :data="execData"
          :max-height="execPanelHeight - 80"
          :single-line="false"
          size="small"
          bordered
          :row-props="(row: any) => ({ style: 'cursor: pointer', onClick: () => emit('select-node', row.nodeCode) })"
        />
        <!-- 日志视图：脚本节点打出的日志，与执行历史详情复用同一组件 -->
        <LogConsole v-if="execViewMode === 'log'" :logs="execResult.scriptLogs || []" :max-height="execPanelHeight - 110" />
        <!-- 映射视图 -->
        <MappedResultTable v-if="execViewMode === 'mapped'" :flow-id="flowId" :data="mappedRawData" />
        <pre v-if="execViewMode === 'json'" style="margin:0; flex:1; overflow:auto; background:#f5f5f5; padding:10px; border-radius:4px; font-size:11px">{{ JSON.stringify(sanitizedExecResult, null, 2) }}</pre>
      </template>
      <div v-else style="text-align:center; padding:30px; color:#999; flex:1">暂无结果</div>
    </div>
  </div>
</template>
