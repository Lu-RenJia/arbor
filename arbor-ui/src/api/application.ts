import request from './request'

export function getAppPage(params: { pageNum: number; pageSize: number; name?: string; flowName?: string }) {
  return request.get('/application/page', { params })
}

export function createApp(data: any) {
  return request.post('/application', data)
}

export function updateApp(id: number, data: any) {
  return request.put(`/application/${id}`, data)
}

export function deleteApp(id: number) {
  return request.delete(`/application/${id}`)
}

export function executeApp(id: number, marketId?: number, envId?: number, signal?: AbortSignal) {
  const params: any = {}
  if (marketId != null) params.marketId = marketId
  if (envId != null) params.envId = envId
  // timeout: 0 = 不限时（应用执行可能较长，不受全局 30s 超时限制）
  return request.post(`/application/execute/${id}`, null, { params, signal, timeout: 0 })
}

export function executeAppWithFile(id: number, formData: FormData, marketId?: number, envId?: number, signal?: AbortSignal) {
  const parts = []
  if (marketId != null) parts.push(`marketId=${marketId}`)
  if (envId != null) parts.push(`envId=${envId}`)
  const qs = parts.length > 0 ? '?' + parts.join('&') : ''
  return request.post(`/application/execute/${id}${qs}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,
    timeout: 0
  })
}

export function shareApp(id: number, marketId: number, envIds: number[]) {
  return request.post(`/application/share/${id}`, { marketId, envIds })
}

export function unshareApp(id: number, marketId: number) {
  return request.delete(`/application/share/${id}/${marketId}`)
}

/** 查询某应用已共享到哪些广场（扁平：同广场多环境多行，前端按 marketId 分组） */
export function getAppSharedMarkets(id: number) {
  return request.get(`/application/share/${id}`)
}
