import request from './request'

export interface FlowScheduleVO {
  id: number
  flowId: number
  cronExpr: string
  enabled: number
  maxExecCount: number | null
  execCount: number
  lastExecTime: string | null
  nextExecTime: string | null
}

/** 获取流程的调度配置 */
export function getFlowSchedule(flowId: number) {
  return request.get(`/flow-schedule/${flowId}`)
}

/** 保存调度配置（仅 admin） */
export function saveFlowSchedule(flowId: number, data: { cronExpr: string; maxExecCount: number | null }) {
  return request.put(`/flow-schedule/${flowId}`, data)
}

/** 启用/停用定时任务（仅 admin） */
export function toggleFlowSchedule(flowId: number, enabled: boolean) {
  return request.put(`/flow-schedule/toggle/${flowId}`, null, { params: { enabled } })
}

/** 计算最近 N 次执行时间 */
export function getScheduleNextTimes(cron: string, count = 10) {
  return request.get('/flow-schedule/next-times', { params: { cron, count } })
}
