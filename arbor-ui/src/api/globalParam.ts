import request from './request'

export function getParamPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/global-param/page', { params })
}

export function getParamList() {
  return request.get('/global-param/list')
}

export function getParamValues(paramName: string) {
  return request.get('/global-param/values', { params: { paramName } })
}

export function createParam(data: { paramName: string; paramValue?: string; remark?: string }) {
  return request.post('/global-param', data)
}

export function updateParam(id: number, data: { paramName: string; remark?: string }) {
  return request.put(`/global-param/${id}`, data)
}

export function deleteParam(id: number) {
  return request.delete(`/global-param/${id}`)
}

export function saveParamValue(paramId: number, envId: number, paramValue: string) {
  return request.put(`/global-param/values/${paramId}/${envId}`, { paramValue })
}

export function deleteParamValue(paramId: number, envId: number) {
  return request.delete(`/global-param/values/${paramId}/${envId}`)
}
