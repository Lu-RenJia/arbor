<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, NTag, NSelect, useMessage, NPopconfirm } from 'naive-ui'
import { getEnvPage, createEnv, updateEnv, deleteEnv } from '../../api/environment'
import { formatDateTime } from '../../utils/format'

const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
// 全站统一：默认按修改时间倒序
const sortField = ref('update_time')
const sortOrder = ref('DESC')
const sortOptions = [
  { label: '修改时间↓', value: 'update_time:DESC' },
  { label: '修改时间↑', value: 'update_time:ASC' },
  { label: '创建时间↓', value: 'create_time:DESC' },
  { label: '创建时间↑', value: 'create_time:ASC' },
  { label: '排序值↑', value: 'sort_order:ASC' },
  { label: '排序值↓', value: 'sort_order:DESC' },
]
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', code: '', remark: '', sortOrder: 0 })

const columns = [
  { title: '序号', key: '_idx', width: 60 },
  { title: '环境名称', key: 'name' },
  { title: '环境代码', key: 'code' },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  {
    title: '默认环境', key: 'isDefault', width: 100, render: (row: any) =>
      row.isDefault === 1 ? h(NTag, { type: 'success', size: 'small' }, { default: () => '默认' }) : ''
  },
  { title: '排序', key: 'sortOrder', width: 80 },
  { title: '创建时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
  { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 160, render: (row: any) => {
      if (row.isDefault === 1) return null
      return h(NSpace, null, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => '确认删除该环境？已关联的参数值将保留并标记为"已停用"'
          })
        ]
      })
    }
  },
]

async function load() {
  loading.value = true
  try {
    const res: any = await getEnvPage({ pageNum: page.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined, sortField: sortField.value, sortOrder: sortOrder.value })
    data.value = (res.list || []).map((r: any, i: number) => ({ ...r, _idx: (page.value - 1) * pageSize.value + i + 1 }))
    total.value = res.total
  } finally { loading.value = false }
}
function handleSortChange(val: string) { const parts = val.split(':'); sortField.value = parts[0]; sortOrder.value = parts[1]; page.value = 1; load() }

function openCreate() {
  isEdit.value = false; editId.value = null
  form.value = { name: '', code: '', remark: '', sortOrder: 0 }
  showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { name: row.name, code: row.code, remark: row.remark || '', sortOrder: row.sortOrder || 0 }
  showModal.value = true
}

async function handleSave() {
  try {
    if (isEdit.value) {
      await updateEnv(editId.value!, form.value)
    } else {
      await createEnv(form.value)
    }
    message.success(isEdit.value ? '修改成功' : '添加成功')
    showModal.value = false; load()
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
}

async function handleDelete(id: number) {
  await deleteEnv(id); message.success('删除成功'); load()
}

onMounted(load)
</script>

<template>
  <div>
    <NSpace justify="space-between" style="margin-bottom: 12px">
      <h3>环境管理</h3>
      <NSpace>
        <NInput v-model:value="searchKeyword" placeholder="搜索环境名称" style="width: 160px" @keyup.enter="page = 1; load()" clearable />
        <NSelect :value="sortField + ':' + sortOrder" :options="sortOptions" @update:value="handleSortChange" style="width: 130px" />
        <NButton @click="page = 1; load()">搜索</NButton>
        <NButton type="primary" @click="openCreate">新增环境</NButton>
      </NSpace>
    </NSpace>
    <NDataTable :columns="columns" :data="data" :loading="loading"
      :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条`, onChange: (p: number) => { page = p; load() }, onUpdatePageSize: (s: number) => { pageSize = s; page = 1; load() } }" remote bordered>
    </NDataTable>

    <NModal v-model:show="showModal" :key="String(isEdit) + String(editId)" preset="card"
      :title="isEdit ? '编辑环境' : '新增环境'" style="width: 520px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80" require-mark-placement="right">
        <NFormItem label="环境名称" required>
          <NInput v-model:value="form.name" placeholder="如：开发环境" />
        </NFormItem>
        <NFormItem label="环境代码" required>
          <NInput v-model:value="form.code" placeholder="如：dev（字母开头，仅含字母数字_-）"
            :disabled="isEdit && editId === 1" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" placeholder="请输入备注" />
        </NFormItem>
        <NFormItem label="排序">
          <NInput :value="String(form.sortOrder)" placeholder="数字越小越靠前" @update:value="(v:string) => form.sortOrder = Number(v) || 0" />
        </NFormItem>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="handleSave">确定</NButton>
        </NSpace>
      </NForm>
    </NModal>
  </div>
</template>
