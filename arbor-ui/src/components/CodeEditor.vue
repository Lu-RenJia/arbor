<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed, nextTick } from 'vue'
import { EditorView, keymap } from '@codemirror/view'
import { EditorState } from '@codemirror/state'
import { defaultKeymap, indentWithTab } from '@codemirror/commands'
import { groovy } from 'codemirror-lang-groovy'
import { sql } from '@codemirror/lang-sql'
import { oneDark } from '@codemirror/theme-one-dark'
import { basicSetup } from 'codemirror'
import type { LanguageSupport } from '@codemirror/language'

const props = withDefaults(defineProps<{
  modelValue: string
  language?: 'groovy' | 'sql'
  readonly?: boolean
  rows?: number
  /** 是否显示“放大到浏览器窗口”按钮 */
  fullscreenable?: boolean
}>(), {
  language: 'groovy',
  readonly: false,
  rows: 14,
  fullscreenable: true
})

function getLanguage(): LanguageSupport {
  return props.language === 'sql' ? sql() : groovy()
}

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const hostRef = ref<HTMLDivElement>()
const editorRef = ref<HTMLDivElement>()
let editorView: EditorView | null = null
const lineHeight = 1.6
/** 普通弹窗下的默认字号 */
const BASE_FONT_SIZE = 13
/** 放大模式下可控字号范围 */
const MIN_FONT_SIZE = 9
const MAX_FONT_SIZE = 36
/** 放大模式字号记忆（浏览器本地，本地不同步到服务端） */
const FONT_SIZE_STORAGE_KEY = 'arbor.code-editor.fullscreen-font-size'

function clampFontSize(size: number): number {
  return Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, size))
}

/** 读取上次放大模式使用的字号；无记录或存储不可用时回退默认 */
function loadStoredFontSize(): number {
  try {
    const raw = localStorage.getItem(FONT_SIZE_STORAGE_KEY)
    const value = raw ? Number(raw) : NaN
    return Number.isFinite(value) ? clampFontSize(value) : BASE_FONT_SIZE
  } catch {
    return BASE_FONT_SIZE
  }
}

/** 是否放大到浏览器窗口（覆盖弹窗，仅保留编辑器本身） */
const maximized = ref(false)
/** 放大模式字号：还原时展示默认字号，下次放大沿用上次的大小 */
const fullscreenFontSize = ref(loadStoredFontSize())
/** 当前实际字号（放大模式用记忆值，普通模式固定默认值） */
const currentFontSize = computed(() => maximized.value ? fullscreenFontSize.value : BASE_FONT_SIZE)

/** 滚轮缩放时的角标提示 */
const zoomHintVisible = ref(false)
let zoomHintTimer: ReturnType<typeof setTimeout> | null = null

function showZoomHint() {
  zoomHintVisible.value = true
  if (zoomHintTimer) clearTimeout(zoomHintTimer)
  zoomHintTimer = setTimeout(() => { zoomHintVisible.value = false }, 900)
}

/**
 * Ctrl + 滚轮：只缩放编辑器字号，取消浏览器页面缩放。
 * 仅在放大模式接管，普通弹窗里保持浏览器原生缩放行为。
 * 监听必须是非 passive 才能 preventDefault。
 */
function onWheel(e: WheelEvent) {
  if (!maximized.value || !e.ctrlKey || e.deltaY === 0) return
  e.preventDefault()
  e.stopPropagation()
  const next = clampFontSize(fullscreenFontSize.value + (e.deltaY < 0 ? 1 : -1))
  if (next === fullscreenFontSize.value) return
  fullscreenFontSize.value = next
  try { localStorage.setItem(FONT_SIZE_STORAGE_KEY, String(next)) } catch { /* 存储不可用时忽略 */ }
  showZoomHint()
  nextTick(() => setTimeout(() => editorView?.requestMeasure(), 0))
}

/** 外层容器：放大时 fixed 铺满视口，box-shadow 充当遮罩 */
const hostStyle = computed(() => maximized.value
  ? {
      position: 'fixed' as const,
      inset: '8px',
      zIndex: 3000,
      background: '#282c34',
      borderRadius: '6px',
      overflow: 'hidden',
      boxShadow: '0 0 0 100vmax rgba(0, 0, 0, 0.45)'
    }
  : { position: 'relative' as const, width: '100%' })

const wrapperStyle = computed(() => ({
  height: maximized.value ? '100%' : `${(props.rows || 14) * lineHeight * BASE_FONT_SIZE}px`,
  fontSize: currentFontSize.value + 'px',
  borderRadius: maximized.value ? '0' : '4px',
  overflow: 'hidden',
  border: maximized.value ? 'none' : '1px solid var(--n-border-color)'
}))

/** 放大/还原：容器尺寸变化后通知 CodeMirror 重新测量 */
async function toggleFullscreen() {
  maximized.value = !maximized.value
  await nextTick()
  setTimeout(() => editorView?.requestMeasure(), 0)
}

onMounted(() => {
  // 非 passive + 捕获阶段：保证 Ctrl+滚轮能 cancel 浏览器缩放，且先于 CodeMirror 自身的滚轮处理
  hostRef.value?.addEventListener('wheel', onWheel, { passive: false, capture: true })
  if (!editorRef.value) return

  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      emit('update:modelValue', update.state.doc.toString())
    }
  })

  const state = EditorState.create({
    doc: props.modelValue || '',
    extensions: [
      basicSetup,
      getLanguage(),
      oneDark,
      EditorView.lineWrapping,
      keymap.of([...defaultKeymap, indentWithTab]),
      updateListener,
      EditorView.editable.of(!props.readonly),
    ]
  })

  editorView = new EditorView({
    state,
    parent: editorRef.value,
  })
})

// 外部修改 modelValue 时同步到编辑器
watch(() => props.modelValue, (newVal) => {
  if (editorView && newVal !== editorView.state.doc.toString()) {
    editorView.dispatch({
      changes: { from: 0, to: editorView.state.doc.length, insert: newVal }
    })
  }
})

onBeforeUnmount(() => {
  hostRef.value?.removeEventListener('wheel', onWheel, { capture: true })
  if (zoomHintTimer) clearTimeout(zoomHintTimer)
  editorView?.destroy()
  editorView = null
})
</script>

<template>
  <div ref="hostRef" class="code-editor-host" :class="{ 'is-maximized': maximized }" :style="hostStyle">
    <div ref="editorRef" class="code-editor-wrapper" :style="wrapperStyle" />
    <div v-if="maximized && zoomHintVisible" class="code-editor-zoom-hint">{{ fullscreenFontSize }}px</div>
    <button
      v-if="fullscreenable"
      type="button"
      class="code-editor-expand"
      :title="maximized ? '还原到弹窗' : '放大到浏览器窗口'"
      @click="toggleFullscreen"
    >{{ maximized ? '🗗 还原' : '⛶ 放大' }}</button>
  </div>
</template>

<style scoped>
.code-editor-host {
  width: 100%;
}
.code-editor-expand {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  padding: 4px 12px;
  font-size: 13px;
  line-height: 20px;
  color: #e8e8e8;
  background: rgba(60, 64, 72, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 5px;
  cursor: pointer;
  opacity: 0.8;
  transition: opacity 0.15s;
}
.code-editor-host:hover .code-editor-expand,
.code-editor-host.is-maximized .code-editor-expand {
  opacity: 1;
}
.code-editor-expand:hover {
  background: rgba(90, 96, 108, 0.95);
  color: #fff;
}
.code-editor-zoom-hint {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
  padding: 4px 14px;
  font-size: 13px;
  line-height: 20px;
  color: #fff;
  background: rgba(0, 0, 0, 0.65);
  border-radius: 4px;
  pointer-events: none;
}
.code-editor-wrapper {
  background: #282c34;
  width: 100%;
}
.code-editor-wrapper :deep(.cm-editor) {
  height: 100%;
  width: 100%;
}
.code-editor-wrapper :deep(.cm-editor .cm-scroller) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  line-height: 1.6;
  overflow-y: auto;
  overflow-x: hidden;
}
.code-editor-wrapper :deep(.cm-editor .cm-content) {
  padding: 8px 0;
  min-width: 100%;
  box-sizing: border-box;
}
.code-editor-wrapper :deep(.cm-editor .cm-line) {
  padding: 0 8px;
}
</style>
