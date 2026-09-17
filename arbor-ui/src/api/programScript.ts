import request from './request'

export function getPsPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/program-script/page', { params })
}

export function createPs(data: any) {
  return request.post('/program-script', data)
}

export function updatePs(id: number, data: any) {
  return request.put(`/program-script/${id}`, data)
}

export function deletePs(id: number) {
  return request.delete(`/program-script/${id}`)
}

export function runPs(scriptId: string) {
  return request.post(`/program-script/run/${scriptId}`)
}

export function testScript(scriptContent: string) {
  return request.post('/program-script/test', scriptContent, {
    headers: { 'Content-Type': 'text/plain' },
    transformRequest: [(data) => data]
  })
}
