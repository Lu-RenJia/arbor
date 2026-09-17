import request from './request'

export function getEnvPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/environment/page', { params })
}

export function getEnvActive() {
  return request.get('/environment/active')
}

export function getEnvDefault() {
  return request.get('/environment/default')
}

export function createEnv(data: { name: string; code: string; remark?: string; sortOrder?: number }) {
  return request.post('/environment', data)
}

export function updateEnv(id: number, data: { name: string; code: string; remark?: string; sortOrder?: number }) {
  return request.put(`/environment/${id}`, data)
}

export function deleteEnv(id: number) {
  return request.delete(`/environment/${id}`)
}
