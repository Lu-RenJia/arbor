<script setup lang="ts">
/**
 * 数据库脚本新建/编辑弹窗（可复用组件）。
 * 与独立「数据库脚本」页的弹窗完全一致：数据源/名称/SQL/返回条数/超时 + 弹窗内执行测试。
 * 供独立页面 DbScript.vue 与流程设计器内联编辑/新建共用，保证 UI 与交互一致。
 *
 * props.script 非空 = 编辑该脚本；为 null = 新建。保存成功后 emit('saved', 保存后的脚本对象)。
 */
import { ref, watch } from 'vue'
import { NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NInputNumber, NRadioGroup, NRadioButton, NDataTable, NPagination, useMessage } from 'naive-ui'
import { createDbScript, updateDbScript, testDbSql } from '../api/dbScript'
import { getDsPage } from '../api/datasource'
import { formatDuration } from '../utils/format'
import CodeEditor from './CodeEditor.vue'

const props = defineProps<{
  /** 编辑的脚本对象（来自列表行/选项）；null 表示新建 */
  script: any
  /** 可选流程ID，新建为内联资源时绑定 */
  flowId?: number | null
  /** 新建时是否创建为内联资源（绑定 flowId） */
  createInline?: boolean
}>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ (e: 'saved', script: any): void }>()

const message = useMessage()

const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', sqlContent: '', remark: '', returnLimit: 10, queryTimeout: null as number | null })
const showTimeout = ref(false)
const editDsId = ref<number | null>(null)
const dsOptions = ref<any[]>([])
const saving = ref(false)
// 弹窗内测试
const testResult = ref<any>(null)
const testTotal = ref(0)
const testPage = ref(1)
const testPageSize = ref(10)
const testViewMode = ref<'table' | 'json'>('table')
const testing = ref(false)
const testError = ref('')
/** 测试执行耗时（毫秒，前端计时） */
const testDuration = ref<number | null>(null)

const PAGE_SIZE_OPTIONS = [5, 10, 20, 50, 100, 200]

/** 将查询结果转换为表格列定义 */
function buildResultColumns(result: any[]): any[] {
  if (!result || result.length === 0) return []
  return Object.keys(result[0]).map(key => ({ title: key, key, ellipsis: { tooltip: true }, width: 140, resizable: true }))
}

function isTableLike(result: any[]): boolean {
  if (!result || result.length === 0) return false
  return Array.isArray(result) && typeof result[0] === 'object' && result[0] !== null
}

/** 弹窗打开时按传入对象初始化表单并加载数据源选项（null=新建空表单） */
// immediate 必需：流程设计器里本组件挂在会翻转的 v-if/v-else 上（按节点类型切 db/ps），
// 切换类型时组件是带着 show=true 被新建的，没有 false→true 跳变，非 immediate 的 watch
// 一次都不会触发，表单停在初始空值上——表现为 SQL 空白、数据源未选中、
// 标题错显「新增」。首次打开必现，关闭后重开才正常。
watch(show, async (v) => {
  if (!v) return
  const row = props.script
  if (row) {
    isEdit.value = true; editId.value = row.id
    form.value = { name: row.name, sqlContent: row.sqlContent || '', remark: row.remark || '', returnLimit: row.returnLimit ?? 10, queryTimeout: row.queryTimeout ?? null }
    showTimeout.value = row.queryTimeout != null
    editDsId.value = row.datasourceId || null
  } else {
    isEdit.value = false; editId.value = null
    form.value = { name: '', sqlContent: '', remark: '', returnLimit: 10, queryTimeout: null }
    showTimeout.value = false
    editDsId.value = null
  }
  testResult.value = null; testError.value = ''
  testViewMode.value = 'table'; testPage.value = 1; testPageSize.value = 10
  try {
    const res: any = await getDsPage({ pageNum: 1, pageSize: 100 })
    dsOptions.value = (res.list || []).map((d: any) => ({ label: d.name, value: d.id }))
  } catch { dsOptions.value = [] }
}, { immediate: true })

async function handleSave() {
  if (!form.value.name.trim()) { message.warning('请输入名称'); return }
  if (!form.value.sqlContent.trim()) { message.warning('请输入SQL语句'); return }
  if (!editDsId.value) { message.warning('请选择数据源'); return }
  saving.value = true
  try {
    const payload: Record<string, any> = { ...form.value, datasourceId: editDsId.value, queryTimeout: showTimeout.value ? form.value.queryTimeout : null }
    let saved: any
    if (isEdit.value) saved = await updateDbScript(editId.value!, payload)
    else {
      // 内联模式新建时绑定 flowId
      if (props.createInline && props.flowId) payload.flowId = props.flowId
      saved = await createDbScript(payload)
    }
    // 编辑时就地回写传入对象（供设计器选项标签即时刷新）
    if (props.script && saved) Object.assign(props.script, saved)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    show.value = false
    emit('saved', saved ?? { ...payload, id: editId.value })
  } catch { /* 拦截器已统一提示错误 */ }
  finally { saving.value = false }
}

async function handleTestInEditor(pageNum?: number, pageSize?: number) {
  if (!editDsId.value) { message.warning('请选择数据源'); return }
  testing.value = true; testResult.value = null; testError.value = ''; testDuration.value = null
  const pn = pageNum ?? testPage.value
  const ps = pageSize ?? testPageSize.value
  const start = Date.now()
  try {
    const result: any = await testDbSql(form.value.sqlContent, editDsId.value, pn, ps)
    testDuration.value = Date.now() - start
    testResult.value = result.list
    testTotal.value = result.total
    testPage.value = pn
    testPageSize.value = ps
    testViewMode.value = 'table'
  } catch {
    testError.value = '执行失败'
    testResult.value = null
  } finally { testing.value = false }
}

function handleTestPageSizeChange(v: number | null) {
  const size = v ?? testPageSize.value
  testPageSize.value = size
  testPage.value = 1
  handleTestInEditor(1, size)
}
</script>

<template>
  <NModal v-model:show="show" preset="card" :title="isEdit ? '编辑数据库脚本' : '新增数据库脚本'" style="width: 950px" :mask-closable="false">
    <NForm :model="form" label-placement="left" label-width="90" require-mark-placement="right">
      <NFormItem label="数据源" required>
        <NSelect v-model:value="editDsId" :options="dsOptions" placeholder="选择执行的数据源" />
      </NFormItem>
      <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="请输入脚本名称" /></NFormItem>
      <NFormItem label="SQL语句" required><CodeEditor v-model="form.sqlContent" language="sql" :rows="12" /></NFormItem>
      <NFormItem label="返回条数" required>
        <NInputNumber v-model:value="form.returnLimit" :min="1" :max="200" placeholder="默认10" style="width: 120px" />
        <span style="margin-left: 6px; color: #999; font-size: 12px">范围 1-200</span>
      </NFormItem>
      <NFormItem label="超时">
        <NSpace align="center">
          <NButton size="small" :type="showTimeout ? 'primary' : 'default'" @click="showTimeout = !showTimeout">
            {{ showTimeout ? '已开启' : '设置超时' }}
          </NButton>
          <template v-if="showTimeout">
            <NInputNumber v-model:value="form.queryTimeout" :min="1" :max="180" placeholder="默认5秒" style="width: 110px" />
            <span style="color: #999; font-size: 12px">秒（最大 180）</span>
          </template>
          <span v-else style="color: #999; font-size: 12px">未设置则默认 5 秒</span>
        </NSpace>
      </NFormItem>
      <!-- 测试结果 -->
      <div v-if="testResult" style="margin-bottom: 12px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px">
        <NSpace justify="space-between" align="center" style="margin-bottom: 8px">
          <span style="font-weight: 600; color: #52c41a">✓ 执行成功（共 {{ testTotal }} 条，耗时 {{ formatDuration(testDuration ?? 0) }}）</span>
          <NRadioGroup v-model:value="testViewMode" size="small">
            <NRadioButton value="table">表格</NRadioButton>
            <NRadioButton value="json">JSON</NRadioButton>
          </NRadioGroup>
        </NSpace>
        <div v-if="testViewMode === 'table' && isTableLike(testResult)" style="overflow-x: auto; max-width: 100%">
          <NDataTable
            :columns="buildResultColumns(testResult)"
            :data="testResult"
            :max-height="300"
            :single-line="false"
            size="small"
            bordered
            :scroll-x="Math.max(buildResultColumns(testResult).length * 140, 800)"
          />
        </div>
        <pre v-else style="margin: 0; background: #fff; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 300px; overflow: auto">{{ JSON.stringify(testResult, null, 2) }}</pre>
        <!-- 分页 -->
        <NSpace v-if="testViewMode === 'table' && testTotal > 0" justify="space-between" align="center" style="margin-top: 8px">
          <NSpace align="center" :size="4">
            <span style="font-size: 12px; color: #999">每页</span>
            <NInputNumber v-model:value="testPageSize" :min="1" :max="200" size="small" style="width: 80px" @update:value="handleTestPageSizeChange" />
            <span style="font-size: 12px; color: #999">条</span>
            <span v-for="n in PAGE_SIZE_OPTIONS" :key="n" style="font-size: 12px; color: #2080f0; cursor: pointer; padding: 0 4px" @click="handleTestPageSizeChange(n)">{{ n }}</span>
          </NSpace>
          <NPagination v-model:page="testPage" :page-size="testPageSize" :item-count="testTotal" size="small" @update:page="(p: number) => handleTestInEditor(p, testPageSize)" />
        </NSpace>
      </div>
      <div v-if="testError" style="margin-bottom: 12px; padding: 12px; background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px">
        <div style="font-weight: 600; color: #ff4d4f; margin-bottom: 4px">✗ 执行失败</div>
        <div style="color: #333; font-size: 13px">{{ testError }}</div>
      </div>
      <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" /></NFormItem>
      <NSpace justify="space-between">
        <NButton type="warning" @click="() => handleTestInEditor()" :loading="testing" ghost>▶ 执行</NButton>
        <NSpace>
          <NButton @click="show = false">取消</NButton>
          <NButton type="primary" @click="handleSave" :loading="saving">确定</NButton>
        </NSpace>
      </NSpace>
    </NForm>
  </NModal>
</template>
