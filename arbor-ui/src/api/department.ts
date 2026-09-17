import request from './request'

export function getDeptPage(params: { pageNum: number; pageSize: number }) {
  return request.get('/department/page', { params })
}

export function getDeptList() {
  return request.get('/department/list')
}

export function getDeptTree() {
  return request.get('/department/treeselect')
}

export function createDept(data: any) {
  return request.post('/department', data)
}

export function updateDept(id: number, data: any) {
  return request.put(`/department/${id}`, data)
}

export function deleteDept(id: number) {
  return request.delete(`/department/${id}`)
}
