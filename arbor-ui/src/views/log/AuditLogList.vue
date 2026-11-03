<script setup lang="ts">
/**
 * 操作审计日志 — 只读列表
 * 展示登录、增删改、导入导出等后台操作的审计记录。
 */
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NInput, NForm, NFormItem, NTag, useMessage } from 'naive-ui'
import { getAuditLogPage } from '../../api/auditLog'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const filters = ref({ moduleName: '', username: '', keyword: '' })
const showSearch = ref(true)

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '操作时间', key: 'createTime', width: 170, render: (row: any) => formatDateTime(row.createTime) },
  { title: '操作人', key: 'username', width: 110, render: (row: any) => row.username || '-' },
  { title: '模块', key: 'moduleName', width: 120, ellipsis: { tooltip: true } },
  { title: '操作', key: 'action', width: 100 },
  { title: '描述', key: 'description', width: 220, ellipsis: { tooltip: true } },
  { title: '请求', key: 'request', width: 230, ellipsis: { tooltip: true }, render: (row: any) => `${row.method || ''} ${row.uri || ''}`.trim() || '-' },
  { title: '结果', key: 'success', width: 80, render: (row: any) => h(NTag, { size: 'small', type: row.success === 1 ? 'success' : 'error', bordered: false }, { default: () => row.success === 1 ? '成功' : '失败' }) },
  { title: '来源IP', key: 'ip', width: 130, ellipsis: { tooltip: true } },
  { title: '失败原因', key: 'errorMsg', ellipsis: { tooltip: true }, render: (row: any) => row.errorMsg || '-' },
]

async function load() {
  loading.value = true
  try {
    const res: any = await getAuditLogPage({
      pageNum: page.value,
      pageSize: pageSize.value,
      moduleName: filters.value.moduleName || undefined,
      username: filters.value.username || undefined,
      keyword: filters.value.keyword || undefined,
    })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function reset() { filters.value = { moduleName: '', username: '', keyword: '' }; page.value = 1; load() }

onMounted(load)
</script>

<template>
  <div>
    <NForm v-show="showSearch" :model="filters" label-placement="left" style="margin-bottom: 12px">
      <NSpace>
        <NFormItem label="模块"><NInput v-model:value="filters.moduleName" placeholder="如：流程" clearable @keyup.enter="search" /></NFormItem>
        <NFormItem label="操作人"><NInput v-model:value="filters.username" placeholder="用户名" clearable @keyup.enter="search" /></NFormItem>
        <NFormItem label="关键字"><NInput v-model:value="filters.keyword" placeholder="描述/URI" clearable @keyup.enter="search" /></NFormItem>
        <NButton type="primary" @click="search">搜索</NButton>
        <NButton @click="reset">重置</NButton>
      </NSpace>
    </NForm>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3 style="margin: 0">操作审计</h3>
      <NButton text @click="showSearch = !showSearch">{{ showSearch ? '收起' : '展开' }}搜索</NButton>
    </NSpace>
    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :pagination="{
        page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100],
        prefix: ({ itemCount }: any) => `共 ${itemCount} 条`,
        onChange: (p: number) => { page = p; load() },
        onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() }
      }"
      remote
      bordered
    />
    <div style="margin-top: 8px; color: #999; font-size: 12px">
      审计日志只读，记录登录、增删改、导入导出等后台操作；请求参数中的密码等敏感字段已脱敏。
    </div>
  </div>
</template>
