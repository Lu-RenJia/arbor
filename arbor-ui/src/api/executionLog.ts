import request from './request'

export function getLogPage(params: {
  pageNum: number
  pageSize: number
  flowName?: string
  executeType?: string
  status?: string
  source?: string
  ownerId?: number | null
  executorId?: number | null
  startTime?: string
  endTime?: string
}) {
  return request.get('/execution-log/page', { params })
}

export function getLogDetail(id: number) {
  return request.get(`/execution-log/${id}`)
}
