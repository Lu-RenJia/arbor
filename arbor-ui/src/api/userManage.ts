import request from './request'

export function getUserPage(params: { pageNum: number; pageSize: number }) {
  return request.get('/user/page', { params })
}

export function getUserList() {
  return request.get('/user/list')
}

export function createUser(data: any) {
  return request.post('/user', data)
}

export function updateUser(id: number, data: any) {
  return request.put(`/user/${id}`, data)
}

export function resetPassword(id: number, password: string) {
  return request.put(`/user/reset-password/${id}`, { password })
}

export function changeUserStatus(id: number, status: string) {
  return request.put(`/user/status/${id}`, { status })
}

export function getUserAuthRole(userId: number) {
  return request.get(`/user/authRole/${userId}`)
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.put('/user/authRole', { userId, roleIds })
}

export function deleteUser(id: number) {
  return request.delete(`/user/${id}`)
}
