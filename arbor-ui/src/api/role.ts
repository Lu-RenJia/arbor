import request from './request'

export function getRolePage(params: { pageNum: number; pageSize: number }) {
  return request.get('/role/page', { params })
}
export function getRoleList() {
  return request.get('/role/list')
}
export function getRoleDetail(id: number) {
  return request.get(`/role/${id}`)
}
export function createRole(data: any) {
  return request.post('/role', data)
}
export function updateRole(id: number, data: any) {
  return request.put(`/role/${id}`, data)
}
export function deleteRole(id: number) {
  return request.delete(`/role/${id}`)
}
export function assignPermissions(roleId: number, permissionIds: number[]) {
  return request.post(`/role/permissions/${roleId}`, permissionIds)
}

export function saveDataScope(roleId: number, dataScope: string) {
  return request.put(`/role/dataScope/${roleId}`, { dataScope })
}
