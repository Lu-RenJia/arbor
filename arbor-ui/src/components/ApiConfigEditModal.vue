<script setup lang="ts">
/**
 * 接口配置新建/编辑弹窗（可复用组件）。
 * 与独立「接口配置」页的弹窗完全一致：名称/请求方式/地址/超时 + Headers/Params/Body 页签 + 弹窗内测试。
 * 供独立页面 ApiConfig.vue 与流程设计器内联编辑共用，保证 UI 与交互一致。
 *
 * props.api 非空 = 编辑该接口；为 null = 新建。保存成功后 emit('saved', 保存后的接口对象)。
 */
import { ref, watch } from 'vue'
import { NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NInputNumber, NTabs, NTabPane, useMessage } from 'naive-ui'
import { createApi, updateApi, testApiRaw } from '../api/apiConfig'
import { downloadFileById } from '../api/file'

const props = defineProps<{
  /** 编辑的接口对象（来自列表行/选项）；null 表示新建 */
  api: any
  /** 可选环境ID，传入后测试接口按该环境解析全局参数 */
  envId?: number | null
  /** 可选流程ID，传入后测试接口会解析该流程的变量池 */
  flowId?: number | null
  /** 新建时是否创建为内联资源（绑定 flowId） */
  createInline?: boolean
}>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ (e: 'saved', api: any): void }>()

const message = useMessage()

const isEdit = ref(false)
const editId = ref<number | null>(null)
const form = ref<Record<string, any>>({ name: '', method: 'GET', url: '', timeout: null as number | null, bodyType: 'none', remark: '' })
const showTimeout = ref(false)
const headersList = ref<{ key: string; value: string }[]>([])
const paramsList = ref<{ key: string; value: string }[]>([])
const urlencodedList = ref<{ key: string; value: string }[]>([])
const bodyContent = ref('')
// 弹窗内测试
const testResult = ref<any>(null)
const testError = ref('')
const testing = ref(false)
const saving = ref(false)
const methodOpts = ['GET', 'POST', 'PUT', 'DELETE'].map(v => ({ label: v, value: v }))

function addRow(list: { key: string; value: string }[]) { list.push({ key: '', value: '' }) }
function removeRow(list: { key: string; value: string }[], idx: number) { list.splice(idx, 1) }
function kvToJson(list: { key: string; value: string }[]) {
  const obj: Record<string, string> = {}
  list.forEach(r => { if (r.key) obj[r.key] = r.value })
  return JSON.stringify(obj)
}
function jsonToKv(json: string): { key: string; value: string }[] {
  try {
    const obj = JSON.parse(json || '{}')
    return Object.entries(obj).map(([k, v]) => ({ key: k, value: String(v) }))
  } catch { return [] }
}

/** 弹窗打开时按传入对象初始化表单（null=新建空表单） */
// immediate 必需：若父级把本组件挂在会翻转的 v-if 上，组件会带着 show=true 被新建，
// 没有 false→true 跳变，非 immediate 的 watch 不会触发，表单会停在初始空值上
watch(show, (v) => {
  if (!v) return
  const row = props.api
  if (row) {
    isEdit.value = true; editId.value = row.id
    form.value = { name: row.name, method: row.method, url: row.url, timeout: row.timeout ?? null, bodyType: row.bodyType || 'none', remark: row.remark || '' }
    showTimeout.value = row.timeout != null
    headersList.value = jsonToKv(row.headers)
    paramsList.value = jsonToKv(row.params)
    bodyContent.value = row.bodyContent || ''
    urlencodedList.value = row.bodyType === 'urlencoded' ? jsonToKv(row.bodyContent) : []
  } else {
    isEdit.value = false; editId.value = null
    form.value = { name: '', method: 'GET', url: '', timeout: null, bodyType: 'none', remark: '' }
    showTimeout.value = false
    headersList.value = []; paramsList.value = []; urlencodedList.value = []; bodyContent.value = ''
  }
  testResult.value = null; testError.value = ''
}, { immediate: true })

/** 组装保存/测试用的请求体 */
function buildPayload(): Record<string, any> {
  const payload: Record<string, any> = {
    ...form.value,
    timeout: showTimeout.value ? form.value.timeout : null,
    headers: kvToJson(headersList.value),
    params: kvToJson(paramsList.value),
  }
  payload.bodyContent = form.value.bodyType === 'urlencoded' ? kvToJson(urlencodedList.value) : bodyContent.value
  return payload
}

async function handleSave() {
  saving.value = true
  try {
    const payload = buildPayload()
    let saved: any
    if (isEdit.value) saved = await updateApi(editId.value!, payload)
    else {
      // 内联模式新建时绑定 flowId
      if (props.createInline && props.flowId) payload.flowId = props.flowId
      saved = await createApi(payload)
    }
    // 编辑时就地回写传入对象（供设计器选项标签即时刷新）
    if (props.api && saved) Object.assign(props.api, saved)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    show.value = false
    emit('saved', saved ?? { ...payload, id: editId.value })
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
  finally { saving.value = false }
}

async function handleTestInEditor() {
  testing.value = true; testResult.value = null; testError.value = ''
  try {
    const res: any = await testApiRaw(buildPayload(), props.envId ?? undefined, props.flowId ?? undefined)
    testResult.value = res
  } catch (e: any) {
    testError.value = e?.response?.data?.message || e?.message || '测试失败'
  } finally { testing.value = false }
}
</script>

<template>
  <NModal v-model:show="show" preset="card" :title="isEdit ? '编辑接口' : '新增接口'" style="width: 820px" :mask-closable="false">
    <NForm :model="form" label-placement="left" label-width="90" require-mark-placement="right">
      <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="请输入接口名称" /></NFormItem>
      <NFormItem label="请求方式" required><NSelect v-model:value="form.method" :options="methodOpts" /></NFormItem>
      <NFormItem label="接口地址" required><NInput v-model:value="form.url" placeholder="支持 ${paramName} 全局参数" /></NFormItem>
      <NFormItem label="超时">
        <NSpace align="center">
          <NButton size="small" :type="showTimeout ? 'primary' : 'default'" @click="showTimeout = !showTimeout">
            {{ showTimeout ? '已开启' : '设置超时' }}
          </NButton>
          <template v-if="showTimeout">
            <NInputNumber v-model:value="form.timeout" :min="1" :max="30" placeholder="默认10秒" style="width: 110px" />
            <span style="color: #999; font-size: 12px">秒（最大 30）</span>
          </template>
          <span v-else style="color: #999; font-size: 12px">未设置则默认 10 秒</span>
        </NSpace>
      </NFormItem>

      <NTabs type="segment">
        <!-- Headers -->
        <NTabPane name="headers" tab="Headers">
          <div style="margin-bottom: 4px">
            <NButton size="tiny" @click="addRow(headersList)">+ 添加</NButton>
          </div>
          <div v-for="(row, idx) in headersList" :key="idx" style="display: flex; gap: 6px; margin-bottom: 4px; align-items: center">
            <NInput v-model:value="row.key" placeholder="Key" size="small" style="width: 160px" />
            <NInput v-model:value="row.value" placeholder="Value" size="small" style="flex: 1" />
            <NButton size="tiny" type="error" @click="removeRow(headersList, idx)" text>×</NButton>
          </div>
          <div v-if="headersList.length === 0" style="color: #bbb; font-size: 12px; padding: 8px 0">暂无，点击"+ 添加"</div>
        </NTabPane>

        <!-- Params -->
        <NTabPane name="params" tab="Params">
          <div style="margin-bottom: 4px">
            <NButton size="tiny" @click="addRow(paramsList)">+ 添加</NButton>
          </div>
          <div v-for="(row, idx) in paramsList" :key="idx" style="display: flex; gap: 6px; margin-bottom: 4px; align-items: center">
            <NInput v-model:value="row.key" placeholder="Key" size="small" style="width: 160px" />
            <NInput v-model:value="row.value" placeholder="Value" size="small" style="flex: 1" />
            <NButton size="tiny" type="error" @click="removeRow(paramsList, idx)" text>×</NButton>
          </div>
          <div v-if="paramsList.length === 0" style="color: #bbb; font-size: 12px; padding: 8px 0">暂无，点击"+ 添加"</div>
        </NTabPane>

        <!-- Body -->
        <NTabPane name="body" tab="Body">
          <NFormItem label="类型" style="margin-bottom: 8px">
            <NSelect v-model:value="form.bodyType" :options="[
              {label:'none', value:'none'}, {label:'JSON', value:'json'},
              {label:'x-www-form-urlencoded', value:'urlencoded'}, {label:'raw', value:'raw'}
            ]" style="width: 220px" />
          </NFormItem>
          <template v-if="form.bodyType === 'urlencoded'">
            <NButton size="tiny" @click="addRow(urlencodedList)" style="margin-bottom: 4px">+ 添加</NButton>
            <div v-for="(row, idx) in urlencodedList" :key="idx" style="display: flex; gap: 6px; margin-bottom: 4px; align-items: center">
              <NInput v-model:value="row.key" placeholder="Key" size="small" style="width: 160px" />
              <NInput v-model:value="row.value" placeholder="Value" size="small" style="flex: 1" />
              <NButton size="tiny" type="error" @click="removeRow(urlencodedList, idx)" text>×</NButton>
            </div>
            <div v-if="urlencodedList.length === 0" style="color: #bbb; font-size: 12px; padding: 8px 0">暂无，点击"+ 添加"</div>
          </template>
          <NFormItem v-else-if="form.bodyType !== 'none'" style="margin-top: 0">
            <NInput v-model:value="bodyContent" type="textarea" :rows="5" placeholder='{"key":"value"}' />
          </NFormItem>
        </NTabPane>
      </NTabs>

      <!-- 测试结果 -->
      <div v-if="testResult" style="margin-bottom: 12px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px">
        <div style="font-weight: 600; color: #52c41a; margin-bottom: 6px">✓ 请求成功</div>
        <div style="font-size: 12px; margin-bottom: 4px">状态码: <strong>{{ testResult.statusCode }}</strong> | 耗时: <strong>{{ testResult.responseTime }}</strong>
          <NButton v-if="testResult.fileId" size="tiny" type="info" style="margin-left: 8px" @click="downloadFileById(testResult.fileId)">📥 {{ testResult.fileName || '下载文件' }}</NButton>
        </div>
        <pre style="margin: 0; background: #fff; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 200px; overflow: auto">{{ typeof testResult.body === 'string' ? testResult.body : JSON.stringify(testResult.body, null, 2) }}</pre>
      </div>
      <div v-if="testError" style="margin-bottom: 12px; padding: 12px; background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px">
        <div style="font-weight: 600; color: #ff4d4f; margin-bottom: 4px">✗ 请求失败</div>
        <div style="color: #333; font-size: 13px">{{ testError }}</div>
      </div>

      <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" /></NFormItem>
      <NSpace justify="space-between">
        <NButton type="warning" @click="handleTestInEditor" :loading="testing" ghost>▶ 测试</NButton>
        <NSpace>
          <NButton @click="show = false">取消</NButton>
          <NButton type="primary" @click="handleSave" :loading="saving">确定</NButton>
        </NSpace>
      </NSpace>
    </NForm>
  </NModal>
</template>
