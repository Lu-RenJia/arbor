import request from './request'

export function getFlowPage(params: { pageNum: number; pageSize: number; name?: string; nodeCount?: number | null; sortField?: string; sortOrder?: string }) {
  return request.get('/flow/page', { params })
}

export function getFlowDetail(id: number) {
  return request.get(`/flow/${id}`)
}

export function createFlow(data: any) {
  return request.post('/flow', data)
}

export function updateFlow(id: number, data: any) {
  return request.put(`/flow/${id}`, data)
}

export function deleteFlow(id: number) {
  return request.delete(`/flow/${id}`)
}

export function updateFlowStatus(id: number, status: number) {
  return request.put(`/flow/status/${id}`, null, { params: { status } })
}

export function saveFlowNodes(id: number, data: any) {
  return request.put(`/flow/nodes/${id}`, data)
}

export function getFlowNodes(id: number) {
  return request.get(`/flow/nodes/${id}`)
}

export function saveFlowExamples(id: number, nodes: any[]) {
  return request.put(`/flow/examples/${id}`, nodes)
}

export function executeFlow(id: number, data?: any, envId?: number, signal?: AbortSignal) {
  // timeout: 0 = 不限时（流程执行可能较长，不受全局 30s 超时限制）
  return request.post(`/flow/execute/${id}`, data || null, { params: envId != null ? { envId } : {}, signal, timeout: 0 })
}

/** 取消正在执行的流程（后端在下一个节点执行前生效） */
export function cancelFlowExecution(id: number) {
  return request.post(`/flow/cancel/${id}`)
}
