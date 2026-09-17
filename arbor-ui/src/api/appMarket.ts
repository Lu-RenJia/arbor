import request from './request'

export function getMarketPage(params: any) {
  return request.get('/app-market/page', { params })
}

export function createMarket(data: any) {
  return request.post('/app-market', data)
}

export function updateMarket(id: number, data: any) {
  return request.put(`/app-market/${id}`, data)
}

export function deleteMarket(id: number) {
  return request.delete(`/app-market/${id}`)
}

export function getMyMarkets() {
  return request.get('/app-market/my')
}

export function getMarketUsers(id: number) {
  return request.get(`/app-market/users/${id}`)
}

export function assignMarketUsers(id: number, userIds: number[]) {
  return request.put(`/app-market/users/${id}`, userIds)
}

export function getMarketApps(id: number, params?: any) {
  return request.get(`/app-market/apps/${id}`, { params })
}
