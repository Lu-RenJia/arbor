import request from './request'

export function getDbScriptPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/db-script/page', { params })
}

export function createDbScript(data: any) {
  return request.post('/db-script', data)
}

export function updateDbScript(id: number, data: any) {
  return request.put(`/db-script/${id}`, data)
}

export function deleteDbScript(id: number) {
  return request.delete(`/db-script/${id}`)
}

export function executeDbScript(scriptId: number, datasourceId: number, pageNum?: number, pageSize?: number) {
  return request.post('/db-script/execute', null, { params: { scriptId, datasourceId, pageNum: pageNum ?? 1, pageSize: pageSize ?? 10 } })
}

export function testDbSql(sqlContent: string, datasourceId: number, pageNum?: number, pageSize?: number) {
  return request.post('/db-script/test', sqlContent, {
    headers: { 'Content-Type': 'text/plain' },
    params: { datasourceId, pageNum: pageNum ?? 1, pageSize: pageSize ?? 10 },
    transformRequest: [(data: any) => data]
  })
}
