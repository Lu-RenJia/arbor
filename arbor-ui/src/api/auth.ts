import request from './request'

export function login(data: { username: string; password: string }) {
  return request.post('/auth/login', data)
}

export function getUserInfo() {
  return request.get('/auth/info')
}

export function logoutApi() {
  return request.post('/auth/logout')
}

/** 获取当前用户基本资料 */
export function getProfile() {
  return request.get('/auth/profile')
}

/** 修改基本资料（昵称 + 性别） */
export function updateProfile(data: { nickname: string; sex: string }) {
  return request.put('/auth/profile', data)
}

/** 修改密码 */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put('/auth/password', data)
}
