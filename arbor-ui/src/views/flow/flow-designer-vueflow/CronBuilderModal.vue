<script setup lang="ts">
/**
 * Cron 表达式可视化构建弹窗
 * 6 位 Spring Cron：秒 分 时 日 月 周
 * 支持按字段选择（全部/间隔/指定）+ 常用模板 + 直接编辑 + 最近 10 次执行时间预览
 */
import { ref, computed, watch } from 'vue'
import { NModal, NTabs, NTabPane, NInputNumber, NCheckboxGroup, NButton, NSpace, NTag, NRadioGroup, NRadioButton, NInput, NEmpty } from 'naive-ui'
import { getScheduleNextTimes } from '../../../api/flowSchedule'

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ (e: 'confirm', cron: string): void }>()

type FieldMode = 'all' | 'interval' | 'specify'

interface FieldState {
  mode: FieldMode
  interval: number
  values: number[]
}

const mkField = (interval: number): FieldState => ({ mode: 'all', interval, values: [] })

const second = ref<FieldState>(mkField(5))
const minute = ref<FieldState>(mkField(5))
const hour = ref<FieldState>(mkField(1))
const day = ref<FieldState & { unspecified: boolean }>({ ...mkField(1), unspecified: false })
const month = ref<FieldState>(mkField(1))
const week = ref<FieldState & { unspecified: boolean }>({ ...mkField(1), unspecified: true })

/** 周字段“不指定”双向绑定 */
const weekUnspecified = computed({
  get: () => week.value.unspecified,
  set: (v: boolean) => { week.value.unspecified = v }
})

/** 直接编辑模式 */
const rawMode = ref(false)
const rawCron = ref('0 0 8 * * ?')

const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => from + i)

const WEEK_NAMES = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

function fieldExpr(f: FieldState, allowUnspecified = false): string {
  if (allowUnspecified && (f as any).unspecified) return '?'
  if (f.mode === 'all') return '*'
  if (f.mode === 'interval') return `*/${f.interval}`
  if (f.mode === 'specify' && f.values.length) return [...f.values].sort((a, b) => a - b).join(',')
  return '*'
}

function weekExpr(): string {
  if ((week.value as any).unspecified) return '?'
  if (week.value.mode === 'all') return '*'
  if (week.value.mode === 'specify' && week.value.values.length) {
    return [...week.value.values].sort((a, b) => a - b).map(v => WEEK_NAMES[v - 1]).join(',')
  }
  return '*'
}

/** 由可视化配置拼出的 cron */
const builtCron = computed(() => {
  // 日/周互斥：指定了周则日为 ?
  if (!(week.value as any).unspecified && week.value.mode === 'specify' && week.value.values.length) {
    return `${fieldExpr(second.value)} ${fieldExpr(minute.value)} ${fieldExpr(hour.value)} ? ${fieldExpr(month.value)} ${weekExpr()}`
  }
  return `${fieldExpr(second.value)} ${fieldExpr(minute.value)} ${fieldExpr(hour.value)} ${fieldExpr(day.value, true)} ${fieldExpr(month.value)} ${weekExpr()}`
})

const cronExpr = computed(() => rawMode.value ? rawCron.value.trim() : builtCron.value)

/** 最近 10 次执行时间预览 */
const nextTimes = ref<string[]>([])
const previewLoading = ref(false)
const previewError = ref('')

let previewTimer: any = null
watch([cronExpr, show], () => {
  if (!show.value || !cronExpr.value) return
  clearTimeout(previewTimer)
  previewTimer = setTimeout(async () => {
    previewLoading.value = true
    previewError.value = ''
    try {
      const res: any = await getScheduleNextTimes(cronExpr.value, 10)
      nextTimes.value = res || []
    } catch (e: any) {
      nextTimes.value = []
      previewError.value = e?.response?.data?.message || 'Cron 表达式不合法'
    } finally {
      previewLoading.value = false
    }
  }, 400)
}, { deep: true })

/** 常用模板 */
const TEMPLATES = [
  { label: '每分钟', cron: '0 * * * * ?' },
  { label: '每 5 分钟', cron: '0 */5 * * * ?' },
  { label: '每 30 分钟', cron: '0 */30 * * * ?' },
  { label: '每小时整点', cron: '0 0 * * * ?' },
  { label: '每天 00:00', cron: '0 0 0 * * ?' },
  { label: '每天 08:00', cron: '0 0 8 * * ?' },
  { label: '工作日 09:00', cron: '0 0 9 ? * MON-FRI' },
  { label: '每周一 09:00', cron: '0 0 9 ? * MON' },
  { label: '每月 1 号 00:00', cron: '0 0 0 1 * ?' },
]

function applyTemplate(cron: string) {
  rawMode.value = true
  rawCron.value = cron
}

function fmt(t: string) {
  return t ? t.replace('T', ' ') : ''
}

function onConfirm() {
  if (!cronExpr.value) return
  emit('confirm', cronExpr.value)
  show.value = false
}
</script>

<template>
  <NModal v-model:show="show" preset="card" title="⏰ 定时任务 Cron 配置" style="width: 640px" :mask-closable="false">
    <NSpace vertical :size="12">
      <!-- 模式切换 -->
      <NRadioGroup v-model:value="rawMode" size="small">
        <NRadioButton :value="false">可视化配置</NRadioButton>
        <NRadioButton :value="true">表达式编辑</NRadioButton>
      </NRadioGroup>

      <!-- 直接编辑 -->
      <template v-if="rawMode">
        <NInput v-model:value="rawCron" placeholder="秒 分 时 日 月 周，如 0 0 8 * * ?" style="font-family: monospace" />
        <NSpace :size="6" align="center">
          <span style="font-size:12px;color:#999">常用：</span>
          <NButton v-for="t in TEMPLATES" :key="t.cron" size="tiny" @click="applyTemplate(t.cron)">{{ t.label }}</NButton>
        </NSpace>
      </template>

      <!-- 可视化配置 -->
      <NTabs v-else type="line" size="small">
        <NTabPane name="second" tab="秒">
          <NRadioGroup v-model:value="second.mode" size="small">
            <NRadioButton value="all">每秒</NRadioButton>
            <NRadioButton value="interval">间隔</NRadioButton>
            <NRadioButton value="specify">指定</NRadioButton>
          </NRadioGroup>
          <NSpace v-if="second.mode === 'interval'" align="center" style="margin-top:8px">
            每 <NInputNumber v-model:value="second.interval" :min="1" :max="59" size="small" style="width:80px" /> 秒
          </NSpace>
          <NCheckboxGroup v-else-if="second.mode === 'specify'" v-model:value="second.values" style="margin-top:8px">
            <NSpace :size="4">
              <NTag v-for="n in range(0, 59)" :key="n" size="small" :type="second.values.includes(n) ? 'primary' : 'default'" style="cursor:pointer" @click="second.values.includes(n) ? second.values.splice(second.values.indexOf(n),1) : second.values.push(n)">{{ n }}</NTag>
            </NSpace>
          </NCheckboxGroup>
        </NTabPane>

        <NTabPane name="minute" tab="分">
          <NRadioGroup v-model:value="minute.mode" size="small">
            <NRadioButton value="all">每分</NRadioButton>
            <NRadioButton value="interval">间隔</NRadioButton>
            <NRadioButton value="specify">指定</NRadioButton>
          </NRadioGroup>
          <NSpace v-if="minute.mode === 'interval'" align="center" style="margin-top:8px">
            每 <NInputNumber v-model:value="minute.interval" :min="1" :max="59" size="small" style="width:80px" /> 分
          </NSpace>
          <NCheckboxGroup v-else-if="minute.mode === 'specify'" v-model:value="minute.values" style="margin-top:8px">
            <NSpace :size="4">
              <NTag v-for="n in range(0, 59)" :key="n" size="small" :type="minute.values.includes(n) ? 'primary' : 'default'" style="cursor:pointer" @click="minute.values.includes(n) ? minute.values.splice(minute.values.indexOf(n),1) : minute.values.push(n)">{{ n }}</NTag>
            </NSpace>
          </NCheckboxGroup>
        </NTabPane>

        <NTabPane name="hour" tab="时">
          <NRadioGroup v-model:value="hour.mode" size="small">
            <NRadioButton value="all">每小时</NRadioButton>
            <NRadioButton value="interval">间隔</NRadioButton>
            <NRadioButton value="specify">指定</NRadioButton>
          </NRadioGroup>
          <NSpace v-if="hour.mode === 'interval'" align="center" style="margin-top:8px">
            每 <NInputNumber v-model:value="hour.interval" :min="1" :max="23" size="small" style="width:80px" /> 小时
          </NSpace>
          <NCheckboxGroup v-else-if="hour.mode === 'specify'" v-model:value="hour.values" style="margin-top:8px">
            <NSpace :size="4">
              <NTag v-for="n in range(0, 23)" :key="n" size="small" :type="hour.values.includes(n) ? 'primary' : 'default'" style="cursor:pointer" @click="hour.values.includes(n) ? hour.values.splice(hour.values.indexOf(n),1) : hour.values.push(n)">{{ n }}</NTag>
            </NSpace>
          </NCheckboxGroup>
        </NTabPane>

        <NTabPane name="day" tab="日">
          <NRadioGroup v-model:value="day.mode" size="small">
            <NRadioButton value="all">每日</NRadioButton>
            <NRadioButton value="interval">间隔</NRadioButton>
            <NRadioButton value="specify">指定</NRadioButton>
          </NRadioGroup>
          <NSpace v-if="day.mode === 'interval'" align="center" style="margin-top:8px">
            每 <NInputNumber v-model:value="day.interval" :min="1" :max="31" size="small" style="width:80px" /> 天
          </NSpace>
          <NCheckboxGroup v-else-if="day.mode === 'specify'" v-model:value="day.values" style="margin-top:8px">
            <NSpace :size="4">
              <NTag v-for="n in range(1, 31)" :key="n" size="small" :type="day.values.includes(n) ? 'primary' : 'default'" style="cursor:pointer" @click="day.values.includes(n) ? day.values.splice(day.values.indexOf(n),1) : day.values.push(n)">{{ n }}</NTag>
            </NSpace>
          </NCheckboxGroup>
          <div style="color:#999;font-size:11px;margin-top:8px">提示：若"周"字段指定了具体星期，"日"将自动置为不指定（?）</div>
        </NTabPane>

        <NTabPane name="month" tab="月">
          <NRadioGroup v-model:value="month.mode" size="small">
            <NRadioButton value="all">每月</NRadioButton>
            <NRadioButton value="interval">间隔</NRadioButton>
            <NRadioButton value="specify">指定</NRadioButton>
          </NRadioGroup>
          <NSpace v-if="month.mode === 'interval'" align="center" style="margin-top:8px">
            每 <NInputNumber v-model:value="month.interval" :min="1" :max="12" size="small" style="width:80px" /> 月
          </NSpace>
          <NCheckboxGroup v-else-if="month.mode === 'specify'" v-model:value="month.values" style="margin-top:8px">
            <NSpace :size="4">
              <NTag v-for="n in range(1, 12)" :key="n" size="small" :type="month.values.includes(n) ? 'primary' : 'default'" style="cursor:pointer" @click="month.values.includes(n) ? month.values.splice(month.values.indexOf(n),1) : month.values.push(n)">{{ n }}月</NTag>
            </NSpace>
          </NCheckboxGroup>
        </NTabPane>

        <NTabPane name="week" tab="周">
          <NRadioGroup v-model:value="week.mode" size="small">
            <NRadioButton value="all">不限制</NRadioButton>
            <NRadioButton value="specify">指定星期</NRadioButton>
          </NRadioGroup>
          <div style="margin-top:8px">
            <label style="font-size:12px;color:#666"><input type="checkbox" v-model="weekUnspecified" /> 不指定（?）— 按“日”字段执行</label>
          </div>
          <NCheckboxGroup v-if="week.mode === 'specify'" v-model:value="week.values" style="margin-top:8px">
            <NSpace :size="4">
              <NTag v-for="(name, i) in WEEK_NAMES" :key="name" size="small" :type="week.values.includes(i + 1) ? 'primary' : 'default'" style="cursor:pointer" @click="week.values.includes(i + 1) ? week.values.splice(week.values.indexOf(i + 1),1) : week.values.push(i + 1)">{{ name }}</NTag>
            </NSpace>
          </NCheckboxGroup>
        </NTabPane>
      </NTabs>

      <!-- 当前表达式 -->
      <div style="background:#f7f8fa;border-radius:6px;padding:10px 12px;display:flex;align-items:center;gap:8px">
        <span style="font-size:12px;color:#999;flex-shrink:0">当前表达式</span>
        <code style="font-size:14px;font-weight:600;color:#18a058">{{ cronExpr || '—' }}</code>
      </div>

      <!-- 最近 10 次执行时间 -->
      <div>
        <div style="font-size:13px;font-weight:600;margin-bottom:6px">最近 10 次执行时间</div>
        <div v-if="previewError" style="color:#d03050;font-size:12px">{{ previewError }}</div>
        <div v-else-if="previewLoading" style="color:#999;font-size:12px">计算中…</div>
        <NSpace v-else-if="nextTimes.length" :size="6">
          <NTag v-for="(t, i) in nextTimes" :key="i" size="small" :type="i === 0 ? 'success' : 'default'">{{ fmt(t) }}</NTag>
        </NSpace>
        <NEmpty v-else description="无结果" size="small" />
      </div>
    </NSpace>

    <template #footer>
      <NSpace justify="end">
        <NButton @click="show = false">取消</NButton>
        <NButton type="primary" :disabled="!cronExpr || !!previewError" @click="onConfirm">确定</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
