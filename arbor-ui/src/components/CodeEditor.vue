<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
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
}>(), {
  language: 'groovy',
  readonly: false,
  rows: 14
})

function getLanguage(): LanguageSupport {
  return props.language === 'sql' ? sql() : groovy()
}

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editorRef = ref<HTMLDivElement>()
let editorView: EditorView | null = null
const lineHeight = 1.6
const fontSize = 13

onMounted(() => {
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
  editorView?.destroy()
  editorView = null
})
</script>

<template>
  <div
    ref="editorRef"
    class="code-editor-wrapper"
    :style="{
      height: `${(rows || 14) * lineHeight * fontSize}px`,
      fontSize: fontSize + 'px',
      borderRadius: '4px',
      overflow: 'hidden',
      border: '1px solid var(--n-border-color)'
    }"
  />
</template>

<style scoped>
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
