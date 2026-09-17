<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NGrid, NGi, NStatistic } from 'naive-ui'
import { getFlowPage } from '../api/flow'
import { getLogPage } from '../api/executionLog'

const flowCount = ref(0)
const runningCount = ref(0)
const totalRunCount = ref(0)
const totalDuration = ref('0ms')

onMounted(async () => {
  try {
    const flowRes: any = await getFlowPage({ pageNum: 1, pageSize: 100 })
    flowCount.value = flowRes.total || 0
    runningCount.value = (flowRes.list || []).filter((f: any) => f.status === 1).length
  } catch { /* */ }
  try {
    const logRes: any = await getLogPage({ pageNum: 1, pageSize: 1000 })
    const logs = logRes.list || []
    totalRunCount.value = logRes.total || 0
    const totalMs = logs.reduce((sum: number, l: any) => sum + (l.duration || 0), 0)
    totalDuration.value = totalMs > 60000 ? `${Math.floor(totalMs / 60000)}m` : `${Math.floor(totalMs / 1000)}s`
  } catch { /* */ }
})
</script>

<template>
  <div>
    <h2 style="margin-bottom: 16px">首页看板</h2>
    <NGrid :cols="4" :x-gap="16" style="margin-bottom: 16px">
      <NGi><NCard><NStatistic label="流程总数" :value="flowCount" /></NCard></NGi>
      <NGi><NCard><NStatistic label="运行中流程" :value="runningCount" /></NCard></NGi>
      <NGi><NCard><NStatistic label="总运行次数" :value="totalRunCount" /></NCard></NGi>
      <NGi><NCard><NStatistic label="总运行时间" :value="totalDuration" /></NCard></NGi>
    </NGrid>
    <NCard title="欢迎使用 Arbor" style="text-align: center; padding: 40px">
      <p>以可视化拖拽方式编排数据处理链路，将数据库脚本、程序脚本与接口调用组织为有序的执行流，实现数据操作的低代码编排与复用。</p>
    </NCard>
  </div>
</template>
