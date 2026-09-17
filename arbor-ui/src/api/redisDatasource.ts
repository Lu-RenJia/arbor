import request from './request'

export function getRedisDsPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/redis-datasource/page', { params })
}
export function createRedisDs(data: any) { return request.post('/redis-datasource', data) }
export function updateRedisDs(id: number, data: any) { return request.put(`/redis-datasource/${id}`, data) }
export function deleteRedisDs(id: number) { return request.delete(`/redis-datasource/${id}`) }
export function testRedisDs(id: number) { return request.post(`/redis-datasource/test/${id}`) }
export function testRedisDsRaw(data: any) { return request.post('/redis-datasource/test', data) }
