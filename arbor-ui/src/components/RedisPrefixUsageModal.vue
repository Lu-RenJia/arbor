<script setup lang="ts">
/**
 * 业务前缀使用情况明细弹窗（D11）—— 回答「这个前缀被哪些流程/哪些节点在用」。
 * <p>
 * 用途有两个，都指向同一件事：改前缀或删除前缀前，先看清影响面。
 * 因为完整 key = {前缀}:{key}，改前缀等于换了一批 key，已写入的缓存会立刻变成孤儿数据。
 * </p>
 * 计数口径务必与后端一致且分开标注：flowCount 按 flowId 去重、nodeCount 不去重，
 * 同一流程两个节点用同一前缀时是「1 个流程 / 2 个节点」，不可合并成「使用 2 次」。
 */
import { ref, watch, h } from 'vue'
import { NModal, NDataTable, NButton, NTag, NSpace, NSpin, NEmpty } from 'naive-ui'
import { useRouter } from 'vue-router'
import { getPrefixUsage } from '../api/redisKeyPrefix'

const props = defineProps<{
  /** 要查看的前缀 id；null 表示未指定（弹窗不查询） */
  prefixId: number | null
  /** 前缀值，仅用于标题展示（避免为了标题再查一次） */
  prefix?: string
}>()

const show = defineModel<boolean>('show', { default: false })

const router = useRouter()

const loading = ref(false)
const usage = ref<any>(null)
const items = ref<any[]>([])

watch(show, async (v) => {
  if (!v) return
  if (props.prefixId == null) return
  loading.value = true
  usage.value = null
  items.value = []
  try {
    const res: any = await getPrefixUsage(props.prefixId)
    usage.value = res
    items.value = res?.items || []
  } catch { /* 拦截器已统一提示错误 */ }
  finally { loading.value = false }
}, { immediate: true })

/** 跳转流程设计器：复用 openReferencedFlow 的既有拼法（name + params.id 字符串） */
function gotoFlow(flowId: number | null) {
  if (flowId == null) return
  router.push({ name: 'FlowDesigner', params: { id: String(flowId) } })
  show.value = false
}

const columns = [
  {
    title: '流程', key: 'flowName', width: 180, ellipsis: { tooltip: true },
    render: (row: any) => row.flowId != null && row.flowName
      ? h(NButton, { size: 'small', text: true, type: 'primary', onClick: () => gotoFlow(row.flowId) }, { default: () => row.flowName })
      // 流程已被删除但节点行还在时，如实显示占位而不是空白，否则计数与明细对不上
      : h('span', { style: 'color:#bbb' }, row.flowName || '(流程已删除)')
  },
  {
    title: '节点', key: 'nodeName', width: 150, ellipsis: { tooltip: true },
    render: (row: any) => h('span', null, `${row.nodeName || '-'}（${row.nodeCode || '-'}）`)
  },
  { title: 'Redis 配置', key: 'scriptName', width: 150, ellipsis: { tooltip: true } },
  { title: '命令', key: 'command', width: 90 },
  {
    // 溯源的关键列：等宽字体 + 允许折行，长 key 也要完整看得见
    title: '完整 Key', key: 'fullKey',
    render: (row: any) => h('span', { style: 'font-family:monospace;font-size:12px;word-break:break-all' }, row.fullKey || '-')
  },
  {
    title: '类型', key: 'inline', width: 80,
    render: (row: any) => h(NTag, { size: 'small', bordered: false, type: row.inline ? 'success' : 'info' },
      { default: () => (row.inline ? '内联' : '全局') })
  },
]
</script>

<template>
  <NModal v-model:show="show" preset="card" title="业务前缀使用情况" style="width: 900px" :mask-closable="false">
    <NSpin :show="loading">
      <template v-if="usage">
        <div style="margin-bottom: 12px; padding: 10px 12px; background: #f7f9fc; border: 1px solid #e8eef7; border-radius: 6px">
          <NSpace align="center" :size="8" style="font-size: 13px">
            <span>前缀</span>
            <strong style="font-family: monospace">{{ usage.prefix || props.prefix }}</strong>
            <NTag size="small" :bordered="false">{{ usage.scriptCount }} 个配置</NTag>
            <!-- 两个数字语义不同：流程数按 flowId 去重，节点数不去重，必须分开标注 -->
            <NTag size="small" type="info" :bordered="false">{{ usage.flowCount }} 个流程</NTag>
            <NTag size="small" type="warning" :bordered="false">{{ usage.nodeCount }} 个节点</NTag>
          </NSpace>
          <div style="margin-top: 6px; color: #999; font-size: 12px; line-height: 1.6">
            完整 key = 业务前缀 + ":" + 配置的 Key；修改前缀会让已写入的缓存变成孤儿数据，需重新生成。
          </div>
        </div>
        <NDataTable
          v-if="items.length > 0"
          :columns="columns"
          :data="items"
          :max-height="420"
          :single-line="false"
          size="small"
          bordered
          :scroll-x="860"
        />
        <NEmpty v-else description="暂无流程使用该前缀" style="padding: 32px 0" />
      </template>
      <div v-else-if="!loading" style="padding: 32px 0">
        <NEmpty description="暂无数据" />
      </div>
      <div v-else style="height: 120px" />
    </NSpin>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="show = false">关闭</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
