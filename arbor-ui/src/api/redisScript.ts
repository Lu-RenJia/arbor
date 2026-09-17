import request from './request'

export function getRedisScriptPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/redis-script/page', { params })
}
export function createRedisScript(data: any) { return request.post('/redis-script', data) }
export function updateRedisScript(id: number, data: any) { return request.put(`/redis-script/${id}`, data) }
export function deleteRedisScript(id: number) { return request.delete(`/redis-script/${id}`) }
export function testRedisScript(id: number) { return request.post(`/redis-script/test/${id}`) }
/** 草稿测试：按当前表单内容执行，不落库（GET&SET 只执行 GET） */
export function testRedisScriptDraft(data: any) { return request.post('/redis-script/test', data) }
