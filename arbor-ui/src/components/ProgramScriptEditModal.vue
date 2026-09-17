<script setup lang="ts">
/**
 * 程序脚本新建/编辑弹窗（可复用组件）。
 * 与独立「程序脚本」页的弹窗完全一致：名称/Groovy脚本/超时 + 弹窗内测试。
 * 供独立页面 ProgramScript.vue 与流程设计器内联编辑/新建共用，保证 UI 与交互一致。
 *
 * props.script 非空 = 编辑该脚本；为 null = 新建。保存成功后 emit('saved', 保存后的脚本对象)。
 */
import { ref, watch } from 'vue'
import { NButton, NSpace, NModal, NForm, NFormItem, NInput, NInputNumber, useMessage } from 'naive-ui'
import { createPs, updatePs, testScript } from '../api/programScript'
import CodeEditor from './CodeEditor.vue'

const EXAMPLE_SCRIPT = `import cn.hutool.core.util.StrUtil
import com.arbor.groovy.util.JsonUtil
import com.arbor.groovy.util.DateUtil
import com.arbor.groovy.util.DataTransformUtil

/**
 * 脚本入口方法（固定签名，框架自动调用）
 * @param params 全局参数 Map<String, String>
 * @return 执行结果，任意类型
 */
def execute(params) {
    // 获取全局参数示例：params.get("global_xxx") 或直接使用变量名 global_xxx
    // 可用工具类：dateUtil / jsonUtil / dataUtil / hutool / log
    // 打日志：log.info("处理{}条", n) / log.debug / log.warn / log.error("出错了", e)
    //   日志会展示在设计器「日志」页签与执行历史详情，请勿打印密码/密钥等敏感信息

    def result = [
        status: "success",
        timestamp: dateUtil.now(),
        message: "Hello Arbor",
        paramCount: params?.size() ?: 0
    ]
    return result
}`

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
const form = ref({ name: '', scriptContent: '', remark: '', queryTimeout: null as number | null })
const showTimeout = ref(false)
const saving = ref(false)
// 弹窗内测试
const testResult = ref<any>(null)
const testing = ref(false)
const testError = ref('')

/** 弹窗打开时按传入对象初始化表单（null=新建空表单+示例模板） */
// immediate 必需：流程设计器里本组件挂在会翻转的 v-if/v-else 上（按节点类型切 db/ps），
// 切换类型时组件是带着 show=true 被新建的，没有 false→true 跳变，非 immediate 的 watch
// 一次都不会触发，表单停在初始空值上——表现为脚本内容空白、标题错显「新增程序脚本」、
// 此时保存会走新建分支而不是更新。首次打开必现，关闭后重开才正常。
watch(show, (v) => {
  if (!v) return
  const row = props.script
  if (row) {
    isEdit.value = true; editId.value = row.id
    form.value = { name: row.name, scriptContent: row.scriptContent || '', remark: row.remark || '', queryTimeout: row.queryTimeout ?? null }
    showTimeout.value = row.queryTimeout != null
  } else {
    isEdit.value = false; editId.value = null
    form.value = { name: '', scriptContent: EXAMPLE_SCRIPT, remark: '', queryTimeout: null }
    showTimeout.value = false
  }
  testResult.value = null; testError.value = ''
}, { immediate: true })

async function handleSave() {
  if (!form.value.name.trim()) { message.warning('请输入名称'); return }
  if (!form.value.scriptContent.trim()) { message.warning('请输入脚本内容'); return }
  saving.value = true
  try {
    const payload: Record<string, any> = { ...form.value, queryTimeout: showTimeout.value ? form.value.queryTimeout : null }
    let saved: any
    if (isEdit.value) saved = await updatePs(editId.value!, payload)
    else {
      // 内联模式新建时绑定 flowId
      if (props.createInline && props.flowId) payload.flowId = props.flowId
      saved = await createPs(payload)
    }
    // 编辑时就地回写传入对象（供设计器选项标签即时刷新）
    if (props.script && saved) Object.assign(props.script, saved)
    message.success(isEdit.value ? '修改成功' : '添加成功')
    show.value = false
    emit('saved', saved ?? { ...payload, id: editId.value })
  } catch (e: any) { message.error(e?.response?.data?.message || e?.message || '操作失败') }
  finally { saving.value = false }
}

// 弹窗内测试脚本：直接传脚本内容到后端执行，无需保存
async function handleTestInEditor() {
  testing.value = true; testResult.value = null; testError.value = ''
  try {
    const result = await testScript(form.value.scriptContent)
    testResult.value = result
  } catch (e: any) {
    testError.value = e?.response?.data?.message || e?.message || '执行失败'
    testResult.value = null
  } finally { testing.value = false }
}
</script>

<template>
  <NModal v-model:show="show" preset="card" :title="isEdit ? '编辑程序脚本' : '新增程序脚本'" style="width: 820px" :mask-closable="false">
    <NForm :model="form" label-placement="left" label-width="90" require-mark-placement="right">
      <NFormItem label="名称" required><NInput v-model:value="form.name" placeholder="请输入脚本名称" /></NFormItem>
      <NFormItem label="脚本内容(Groovy)" required>
        <CodeEditor v-model="form.scriptContent" :rows="16" />
      </NFormItem>
      <!-- 测试结果展示 -->
      <div v-if="testResult" style="margin-bottom: 12px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px">
        <div style="font-weight: 600; color: #52c41a; margin-bottom: 6px">✓ 执行成功</div>
        <pre style="margin: 0; background: #fff; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 200px; overflow: auto">{{ JSON.stringify(testResult, null, 2) }}</pre>
      </div>
      <div v-if="testError" style="margin-bottom: 12px; padding: 12px; background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px">
        <div style="font-weight: 600; color: #ff4d4f; margin-bottom: 4px">✗ 执行失败</div>
        <div style="color: #333; font-size: 13px">{{ testError }}</div>
      </div>
      <NFormItem label="超时">
        <NSpace align="center">
          <NButton size="small" :type="showTimeout ? 'primary' : 'default'" @click="showTimeout = !showTimeout">
            {{ showTimeout ? '已开启' : '设置超时' }}
          </NButton>
          <template v-if="showTimeout">
            <NInputNumber v-model:value="form.queryTimeout" :min="1" :max="300" placeholder="默认60秒" style="width: 110px" />
            <span style="color: #999; font-size: 12px">秒（最大 300）</span>
          </template>
          <span v-else style="color: #999; font-size: 12px">未设置则默认 60 秒</span>
        </NSpace>
      </NFormItem>
      <NFormItem label="备注"><NInput v-model:value="form.remark" placeholder="请输入备注" /></NFormItem>
      <NSpace justify="space-between">
        <NButton type="warning" @click="handleTestInEditor" :loading="testing" ghost>▶ 测试脚本</NButton>
        <NSpace>
          <NButton @click="show = false">取消</NButton>
          <NButton type="primary" @click="handleSave" :loading="saving">确定</NButton>
        </NSpace>
      </NSpace>
    </NForm>
  </NModal>
</template>
