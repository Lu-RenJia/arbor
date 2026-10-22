<script setup lang="ts">
/**
 * 脚本日志控制台：流程设计器底部面板与执行历史详情共用的纯展示组件。
 * 数据由父组件传入（后端 ScriptLogEntry 列表），组件自身不发请求。
 * 每行按「时间 [级别] logger - 消息」渲染，整行按级别着色，支持级别筛选降噪。
 */
import { computed, ref } from 'vue'
import { NCheckbox, NCheckboxGroup, NSpace } from 'naive-ui'

/** 与后端 ScriptLogEntry 对外 JSON 结构一致（timestamp 已被 @JsonIgnore） */
interface ScriptLogLine {
  level: string
  time: string
  logger: string
  message: string
  nodeCode?: string
  nodeName?: string
}

const props = withDefaults(defineProps<{
  logs?: ScriptLogLine[] | null
  /** 日志区最大高度（px），超出内部滚动 */
  maxHeight?: number
  emptyText?: string
}>(), {
  logs: () => [],
  maxHeight: 300,
  emptyText: '本次执行没有脚本日志输出',
})

/** 可筛选级别，与后端捕获范围一致（TRACE 不捕获） */
const LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR']

const LEVEL_COLORS: Record<string, string> = {
  ERROR: '#cf1322',
  WARN: '#d48806',
  INFO: '#333333',
  DEBUG: '#999999',
}

/** 收集器达条数上限时追加的固定提示前缀，据此判断是否被截断 */
const TRUNCATED_PREFIX = '日志条数已达上限'

/** 默认全选，用户按需取消 DEBUG 等降噪 */
const checkedLevels = ref<string[]>([...LEVELS])

const allLogs = computed<ScriptLogLine[]>(() => props.logs || [])

const visibleLogs = computed(() =>
  allLogs.value.filter((l) => checkedLevels.value.includes(l.level))
)

/** 是否发生过条数截断（后端丢弃了超出上限的日志） */
const isTruncated = computed(() =>
  allLogs.value.some((l) => (l.message || '').startsWith(TRUNCATED_PREFIX))
)

/** 筛选后条数与总条数不一致时才显示「显示 M / 」前缀 */
const countText = computed(() => {
  const total = allLogs.value.length
  const shown = visibleLogs.value.length
  return shown === total ? `共 ${total} 条` : `显示 ${shown} / 共 ${total} 条`
})

function levelColor(level: string): string {
  return LEVEL_COLORS[level] || '#333333'
}

/** 级别定宽，保证多行左侧对齐 */
function levelText(level: string): string {
  return (level || '').padEnd(5, ' ')
}

/** 供父组件复制当前筛选后可见的日志（与页面显示一致） */
function getCopyText(): string {
  return visibleLogs.value
    .map(l => `${l.time} [${levelText(l.level)}] ${l.logger} - ${l.message}`)
    .join('\n')
}

defineExpose({ getCopyText })
</script>

<template>
  <div style="display: flex; flex-direction: column; min-height: 0">
    <NSpace align="center" justify="space-between" :size="12" style="margin-bottom: 6px; flex-shrink: 0">
      <NCheckboxGroup v-model:value="checkedLevels">
        <NSpace :size="10">
          <NCheckbox v-for="lv in LEVELS" :key="lv" :value="lv" :label="lv" size="small" />
        </NSpace>
      </NCheckboxGroup>
      <NSpace align="center" :size="10">
        <span v-if="isTruncated" style="font-size: 12px; color: #d48806">日志过多，已截断</span>
        <span style="font-size: 12px; color: #999">{{ countText }}</span>
      </NSpace>
    </NSpace>

    <div
      v-if="visibleLogs.length"
      :style="{
        maxHeight: maxHeight + 'px',
        overflow: 'auto',
        background: '#fafafa',
        border: '1px solid #eee',
        borderRadius: '4px',
        padding: '6px 10px',
        fontFamily: 'Consolas, Monaco, Menlo, monospace',
        fontSize: '12px',
        lineHeight: '18px',
      }"
    >
      <div
        v-for="(l, i) in visibleLogs"
        :key="i"
        :style="{ color: levelColor(l.level), whiteSpace: 'pre-wrap', wordBreak: 'break-all' }"
      >
        <span style="color: #999">{{ l.time }}</span>
        <span style="font-weight: 600"> [{{ levelText(l.level) }}] </span>
        <span style="color: #1677ff">{{ l.logger }}</span>
        <span> - {{ l.message }}</span>
      </div>
    </div>
    <div
      v-else
      :style="{
        height: Math.min(maxHeight, 120) + 'px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#fafafa',
        border: '1px solid #eee',
        borderRadius: '4px',
        color: '#999',
        fontSize: '12px',
      }"
    >
      {{ allLogs.length ? '当前级别筛选下没有日志' : emptyText }}
    </div>
  </div>
</template>
