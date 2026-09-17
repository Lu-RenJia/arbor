import request from './request'

export function getPermissionList() {
  return request.get('/permission/list')
}
