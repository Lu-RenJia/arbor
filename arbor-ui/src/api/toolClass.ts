import request from './request'

/** 查询所有工具类列表 */
export function getToolClassList() {
  return request.get('/tool-class/list')
}

/** 查看工具类详情 */
export function getToolClass(id: number) {
  return request.get(`/tool-class/${id}`)
}

/** 创建工具类 */
export function createToolClass(data: { name: string; scriptContent: string; remark?: string }) {
  return request.post('/tool-class', data)
}

/** 更新工具类 */
export function updateToolClass(id: number, data: { name: string; scriptContent: string; remark?: string }) {
  return request.put(`/tool-class/${id}`, data)
}

/** 删除工具类 */
export function deleteToolClass(id: number) {
  return request.delete(`/tool-class/${id}`)
}
