<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { NButton, NSpace, NRadioGroup, NRadioButton } from 'naive-ui'
import { applyFieldMapping } from '../api/fieldMapping'

const props = defineProps<{
  flowId: number
  data: any  // 原始结果数据（对象或数组）
}>()

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
  if (!props.flowId || props.data == null || typeof props.data !== 'object') {
    columns.value = []; rows.value = []; return
  }
  loading.value = true
  try {
    const res: any = await applyFieldMapping(props.flowId, props.data)
    columns.value = res.columns || []
    rows.value = res.rows || []
  } catch {
    // 映射失败，回退到原始列
    const raw = normalizeData(props.data)
    if (raw.length > 0) {
      columns.value = Object.keys(raw[0]).map(k => ({ fieldName: k, fieldLabel: k, mappingJson: null }))
    } else {
      columns.value = []
    }
    rows.value = raw
  } finally {
    loading.value = false
    page.value = 1
  }
}

function normalizeData(raw: any): any[] {
  if (Array.isArray(raw)) return raw
  if (raw && typeof raw === 'object') return [raw]
  return []
}

watch(() => [props.flowId, props.data], () => load(), { immediate: true })
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
            <th v-for="col in columns" :key="col.fieldName"
              style="border:1px solid #e8e8e8; padding:6px 8px; background:#fafafa; text-align:left; white-space:nowrap; position:sticky; top:0">
              {{ col.fieldLabel }}
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
