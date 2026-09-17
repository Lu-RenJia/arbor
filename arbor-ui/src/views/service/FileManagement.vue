<script setup lang="ts">
/**
 * 文件管理 — 查看系统中的示例文件及其关联的流程和应用
 */
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, useMessage, NPopconfirm } from 'naive-ui'
import { getFilePage, deleteFile, downloadFileById } from '../../api/file'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '文件名', key: 'fileName', ellipsis: { tooltip: true } },
  { title: '关联流程', key: 'flowName', width: 150, render: (row: any) => row.flowName || '-' },
  { title: '关联应用', key: 'appNames', width: 150, render: (row: any) => row.appNames || '-' },
  { title: '文件大小', key: 'fileSize', width: 90, render: (row: any) => formatFileSize(row.fileSize) },
  { title: '类型', key: 'contentType', width: 90, render: (row: any) => row.contentType || '-' },
  { title: '上传时间', key: 'createTime', width: 160, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 160, render: (row: any) => formatDateTime(row.updateTime) },
  { title: '操作', key: 'actions', width: 160, render: (row: any) => {
    return h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => handleDownload(row.id) }, { default: () => '下载' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确认删除？'
        })
      ]
    })
  }},
]

function formatFileSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function load() {
  loading.value = true
  try {
    const res: any = await getFilePage({ pageNum: page.value, pageSize: pageSize.value })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}

function handleDownload(id: number) {
  downloadFileById(id)
}

async function handleDelete(id: number) {
  await deleteFile(id)
  message.success('删除成功')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>文件管理</h3>
    </NSpace>
    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote
      bordered
    />
    <div style="margin-top: 8px; color: #999; font-size: 12px">
      仅显示示例文件（流程中上传的模板文件），用户执行时上传的临时文件不在此列
    </div>
  </div>
</template>
