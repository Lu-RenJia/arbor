import request from './request'

export function getMenuTree() {
  return request.get('/menu/tree')
}
export function getMyMenus() {
  return request.get('/menu/my')
}
export function getMenuList() {
  return request.get('/menu/list')
}
export function getMenuDetail(id: number) {
  return request.get(`/menu/${id}`)
}
export function createMenu(data: any) {
  return request.post('/menu', data)
}
export function updateMenu(id: number, data: any) {
  return request.put(`/menu/${id}`, data)
}
export function deleteMenu(id: number) {
  return request.delete(`/menu/${id}`)
}
