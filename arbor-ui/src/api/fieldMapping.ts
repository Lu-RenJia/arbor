import request from './request'

export function getFieldMappings(flowId: number) {
  return request.get(`/field-mapping/list/${flowId}`)
}

export function createFieldMapping(flowId: number, data: any) {
  return request.post(`/field-mapping/create/${flowId}`, data)
}

export function updateFieldMapping(flowId: number, id: number, data: any) {
  return request.put(`/field-mapping/update/${flowId}/${id}`, data)
}

export function deleteFieldMapping(flowId: number, id: number) {
  return request.delete(`/field-mapping/delete/${flowId}/${id}`)
}

export function batchSaveFieldMappings(flowId: number, list: any[]) {
  return request.post(`/field-mapping/batch/${flowId}`, list)
}

export function applyFieldMapping(flowId: number, data: any) {
  return request.post(`/field-mapping/apply/${flowId}`, data)
}
