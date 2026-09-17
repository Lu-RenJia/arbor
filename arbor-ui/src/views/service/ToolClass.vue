<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { NDataTable, NButton, NSpace, NModal, NForm, NFormItem, NInput, useMessage, NPopconfirm, NAlert, NTag } from 'naive-ui'
import { getToolClassList, createToolClass, updateToolClass, deleteToolClass } from '../../api/toolClass'
import { formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import CodeEditor from '../../components/CodeEditor.vue'

const EXAMPLE_TOOL = `/**
 * 工具类示例：静态方法类
 * 所有程序脚本中可直接使用类名调用：
 *   MyUtil.formatName("hello")
 */
class MyUtil {
    static String formatName(String name) {
        return name?.trim()?.toUpperCase()
    }

    static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty()
    }
}`

const message = useMessage()
const data = ref<any[]>([])
const loading = ref(false)
const showModal = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', scriptContent: '', remark: '' })
const isAdmin = ref(false)
const searchKeyword = ref('')

const filteredData = computed(() => {
  if (!searchKeyword.value) return data.value
  const kw = searchKeyword.value.toLowerCase()
  return data.value.filter((r: any) =>
    (r.name && r.name.toLowerCase().includes(kw)) ||
    (r.toolClassName && r.toolClassName.toLowerCase().includes(kw))
  )
})

// 判断当前用户是否为 admin（从 auth store 内存状态读取，不依赖 localStorage）
onMounted(() => {
  const auth = useAuthStore()
  isAdmin.value = auth.isAdmin
  load()
})

const columns = computed(() => {
  const cols: any[] = [
    { title: '序号', key: '_idx', width: 60 },
    { title: '类名', key: 'toolClassName', width: 160, render: (row: any) => h(NTag, { type: 'info', size: 'small' }, { default: () => row.toolClassName }) },
    { title: '名称', key: 'name' },
    { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
    { title: '创建时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
    { title: '修改时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime) },
  ]
  if (isAdmin.value) {
    cols.push({
      title: '操作', key: 'actions', width: 200, render: (row: any) => {
        return h(NSpace, null, {
          default: () => [
            h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
            h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
              trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
              default: () => '确认删除？删除后引用此工具类的脚本将执行失败。'
            })
          ]
        })
      }
    })
  }
  return cols
})

async function load() {
  loading.value = true
  try {
    const res: any = await getToolClassList()
    data.value = (res || []).map((r: any, i: number) => ({ ...r, _idx: i + 1 }))
  } catch (e: any) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { name: '', scriptContent: EXAMPLE_TOOL, remark: '' }
  showModal.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.value = { name: row.name, scriptContent: row.scriptContent, remark: row.remark || '' }
  showModal.value = true
}

async function handleSave() {
  if (!form.value.name.trim()) { message.warning('请输入工具类名称'); return }
  if (!form.value.scriptContent.trim()) { message.warning('请输入工具类代码'); return }
  try {
    if (isEdit.value && editId.value) {
      await updateToolClass(editId.value, form.value)
      message.success('修改成功')
    } else {
      const res: any = await createToolClass(form.value)
      // 检查是否有警告信息（超过50个）
      if (res && typeof res === 'string' && res.includes('提示')) {
        message.warning(res)
      } else {
        message.success('创建成功')
      }
    }
    showModal.value = false
    load()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  }
}

async function handleDelete(id: number) {
  try {
    await deleteToolClass(id)
    message.success('删除成功')
    load()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
}
</script>

<template>
  <div style="padding: 20px;">
    <NSpace vertical :size="16">
      <NSpace justify="space-between" align="center">
        <NSpace align="center" :size="12">
          <h3 style="margin: 0;">工具类管理</h3>
          <NTag type="warning" size="small">全局共享</NTag>
        </NSpace>
        <NSpace>
          <NInput v-model:value="searchKeyword" placeholder="搜索名称/类名" style="width: 160px" clearable />
          <NButton v-if="isAdmin" type="primary" @click="openCreate">新增工具类</NButton>
        </NSpace>
      </NSpace>

      <NAlert type="info" :bordered="false">
        工具类为全局共享资源，所有用户的程序脚本中可直接使用类名调用（如 <code>MyUtil.formatName("hello")</code> 或 <code>new Student()</code>）。
        <span v-if="!isAdmin">仅管理员可创建和修改。</span>
      </NAlert>

      <NDataTable :columns="columns" :data="filteredData" :loading="loading" :bordered="false" size="small"
        :pagination="{ pageSize: 10, showSizePicker: true, pageSizes: [10, 20, 50, 100], prefix: ({ itemCount }: any) => `共 ${itemCount} 条` }" />
    </NSpace>

    <!-- 新增/编辑弹窗 -->
    <NModal
      v-model:show="showModal"
      preset="card"
      :title="isEdit ? '编辑工具类' : '新增工具类'"
      style="width: 750px;"
      :mask-closable="false"
      :key="isEdit ? (editId ?? 0) : 'create'"
    >
      <NForm label-placement="left" label-width="80">
        <NFormItem label="名称" required>
          <NInput v-model:value="form.name" placeholder="工具类名称（中文描述，如：学生工具类）" />
        </NFormItem>
        <NFormItem label="代码" required>
          <div style="width: 100%;">
            <CodeEditor v-model="form.scriptContent" language="groovy" :rows="17" />
          </div>
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" type="textarea" placeholder="可选备注" :rows="2" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="handleSave">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
