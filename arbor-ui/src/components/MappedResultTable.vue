<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { NButton, NSpace, NRadioGroup, NRadioButton, useMessage } from 'naive-ui'
import { applyFieldMapping } from '../api/fieldMapping'
import { rowsToPasteText } from '../utils/resultExport'

const props = defineProps<{
  flowId: number
  data: any  // 原始结果数据（对象或数组）
}>()

const message = useMessage()
const loading = ref(false)
const columns = ref<any[]>([])
const rows = ref<any[]>([])
const page = ref(1)
const pageSize = ref(5)

const pageSizeOptions = [
  { label: '5条/页', value: 5 },
  { label: '10条/页', value: 10 },
  { label: '15条/页', value: 15 },
  { label: '20条/页', value: 20 },
  { label: '不分页', value: 0 },
]

const totalPages = computed(() => {
  if (pageSize.value === 0) return 0
  return Math.ceil(rows.value.length / pageSize.value)
})

const pagedRows = computed(() => {
  if (pageSize.value === 0) return rows.value
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

async function load() {
  if (!props.flowId || props.data == null) {
    columns.value = []; rows.value = []; return
  }
  // 标量结果（字符串 / 数字 / 布尔）没有字段可映射，本地渲染成单列
  if (!Array.isArray(props.data) && !isPlainObject(props.data)) {
    columns.value = [{ fieldName: 'value', fieldLabel: '值', mappingJson: null }]
    rows.value = [{ value: props.data }]
    loading.value = false
    page.value = 1
    return
  }
  loading.value = true
  try {
    const res: any = await applyFieldMapping(props.flowId, props.data)
    columns.value = res.columns || []
    rows.value = res.rows || []
  } catch {
    // 映射失败，回退到原始列（列取所有行的并集，不只看首行）
    const raw = normalizeData(props.data)
    const keys: string[] = []
    const seen = new Set<string>()
    for (const row of raw) {
      for (const k of Object.keys(row)) {
        if (!seen.has(k)) { seen.add(k); keys.push(k) }
      }
    }
    columns.value = keys.map(k => ({ fieldName: k, fieldLabel: k, mappingJson: null }))
    rows.value = raw
  } finally {
    loading.value = false
    page.value = 1
  }
}

function isPlainObject(value: any): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** 标量数组（如 ["a","b"]）包装成单列，避免把字符串当对象取 key */
function normalizeData(raw: any): any[] {
  if (Array.isArray(raw)) return raw.map(v => (isPlainObject(v) ? v : { value: v }))
  if (isPlainObject(raw)) return [raw]
  if (raw !== null && raw !== undefined) return [{ value: raw }]
  return []
}

/** 复制整列全部行：不含表头，空值保留空行 */
async function copyColumn(col: any) {
  const text = rowsToPasteText([{ key: col.fieldName }], rows.value, false)
  if (!text) { message.warning('该列没有数据'); return }
  try {
    await navigator.clipboard.writeText(text)
    message.success(`已复制「${col.fieldLabel}」${rows.value.length} 行`)
  } catch {
    message.error('复制失败')
  }
}

watch(() => [props.flowId, props.data], () => load(), { immediate: true })

/** 供父组件复制当前映射结果（表头用映射后的显示名） */
defineExpose({
  getPasteText(header = true): string {
    return rowsToPasteText(
      columns.value.map(c => ({ key: c.fieldName, label: c.fieldLabel })),
      rows.value,
      header
    )
  }
})
</script>

<template>
  <div v-if="loading" style="text-align:center; padding:16px; color:#999; font-size:12px">加载中…</div>
  <div v-else-if="columns.length === 0" style="text-align:center; padding:16px; color:#999; font-size:12px">暂无数据</div>
  <div v-else style="display:flex; flex-direction:column">
    <div style="overflow:auto; max-height:200px">
      <table style="width:100%; border-collapse:collapse; font-size:12px">
        <thead>
          <tr>
            <th style="border:1px solid #e8e8e8; padding:6px 8px; background:#fafafa; text-align:center; width:40px; white-space:nowrap; position:sticky; top:0">#</th>
            <th v-for="col in columns" :key="col.fieldName" class="mapped-result-th"
              style="border:1px solid #e8e8e8; padding:6px 8px; background:#fafafa; text-align:left; white-space:nowrap; position:sticky; top:0">
              <div class="mapped-result-th-label">
                <span>{{ col.fieldLabel }}</span>
                <button
                  type="button"
                  class="col-copy-btn"
                  :title="`复制整列「${col.fieldLabel}」（不含表头）`"
                  @click.stop="copyColumn(col)"
                >📋</button>
              </div>
              <div style="font-size:10px; color:#999; font-weight:normal">{{ col.fieldName }}</div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in pagedRows" :key="ri">
            <td style="border:1px solid #f0f0f0; padding:4px 8px; text-align:center; color:#999; font-size:11px">
              {{ (page - 1) * (pageSize || 1) + ri + 1 }}
            </td>
            <td v-for="col in columns" :key="col.fieldName"
              style="border:1px solid #f0f0f0; padding:4px 8px">
              {{ row[col.fieldName] != null ? row[col.fieldName] : '' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <NSpace justify="center" align="center" style="margin-top:6px; flex-shrink:0" :size="8">
      <template v-if="pageSize > 0">
        <NButton size="tiny" :disabled="page <= 1" @click="page = 1">«</NButton>
        <NButton size="tiny" :disabled="page <= 1" @click="page--">‹</NButton>
        <span style="font-size:11px; color:#666">{{ page }} / {{ totalPages || 1 }}</span>
        <NButton size="tiny" :disabled="page >= totalPages" @click="page++">›</NButton>
        <NButton size="tiny" :disabled="page >= totalPages" @click="page = totalPages">»</NButton>
      </template>
      <NRadioGroup v-model:value="pageSize" size="small">
        <NRadioButton v-for="o in pageSizeOptions" :key="o.value" :value="o.value" :label="o.label" />
      </NRadioGroup>
    </NSpace>
  </div>
</template>

<style scoped>
.mapped-result-th-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.col-copy-btn {
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  opacity: 0.55;
  transition: opacity 0.15s;
}
.mapped-result-th:hover .col-copy-btn,
.col-copy-btn:focus-visible,
.col-copy-btn:hover {
  opacity: 1;
}
</style>
