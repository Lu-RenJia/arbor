import request from './request'

/** 某数据源下的前缀列表（轻量，不带使用计数）—— Redis 配置弹窗的前缀下拉用 */
export function getPrefixByDatasource(datasourceId: number) {
  return request.get(`/redis-key-prefix/datasource/${datasourceId}`)
}

/**
 * 某数据源下的前缀列表 + 使用计数 —— 数据源页的前缀管理表格用（D11）。
 * 与上面的轻量接口分开，避免配置弹窗选前缀时白跑一轮计数查询。
 */
export function getPrefixUsageSummary(datasourceId: number) {
  return request.get(`/redis-key-prefix/datasource/${datasourceId}/usage`)
}

/** 某前缀被哪些流程/节点使用的明细（D11，点开才查） */
export function getPrefixUsage(id: number) {
  return request.get(`/redis-key-prefix/${id}/usage`)
}

export function createPrefix(data: any) { return request.post('/redis-key-prefix', data) }
export function updatePrefix(id: number, data: any) { return request.put(`/redis-key-prefix/${id}`, data) }
export function deletePrefix(id: number) { return request.delete(`/redis-key-prefix/${id}`) }
