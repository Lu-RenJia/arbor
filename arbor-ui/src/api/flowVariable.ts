import request from './request'

export function getFlowVariables(flowId: number) {
  return request.get(`/flow-variable/list/${flowId}`)
}

export function createFlowVariable(flowId: number, data: any) {
  return request.post(`/flow-variable/create/${flowId}`, data)
}

export function updateFlowVariable(flowId: number, id: number, data: any) {
  return request.put(`/flow-variable/update/${flowId}/${id}`, data)
}

export function deleteFlowVariable(_flowId: number, id: number) {
  return request.delete(`/flow-variable/delete/${id}`)
}

export function deleteFlowVariablesBatch(flowId: number, ids: number[]) {
  return request.post(`/flow-variable/batch-delete/${flowId}`, ids)
}
