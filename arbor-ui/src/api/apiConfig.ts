import request from './request'

export function getApiPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/api-config/page', { params })
}

export function createApi(data: any) {
  return request.post('/api-config', data)
}

export function updateApi(id: number, data: any) {
  return request.put(`/api-config/${id}`, data)
}

export function deleteApi(id: number) {
  return request.delete(`/api-config/${id}`)
}

export function testApi(id: number, envId?: number) {
  return request.post(`/api-config/test/${id}`, null, { params: envId ? { envId } : {} })
}

export function testApiRaw(data: any, envId?: number, flowId?: number) {
  const params: Record<string, any> = {}
  if (envId) params.envId = envId
  if (flowId) params.flowId = flowId
  return request.post('/api-config/test', data, { params })
}
