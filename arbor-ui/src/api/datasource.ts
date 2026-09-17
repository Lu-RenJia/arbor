import request from './request'

export function getDsPage(params: { pageNum: number; pageSize: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  return request.get('/datasource/page', { params })
}

export function createDs(data: any) {
  return request.post('/datasource', data)
}

export function updateDs(id: number, data: any) {
  return request.put(`/datasource/${id}`, data)
}

export function deleteDs(id: number) {
  return request.delete(`/datasource/${id}`)
}

export function testDs(id: number) {
  return request.post(`/datasource/test/${id}`)
}

/** 测试连接（未保存的配置，直接传入表单数据） */
export function testDsRaw(data: any) {
  return request.post('/datasource/test', data)
}
