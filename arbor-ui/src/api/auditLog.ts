import request from './request'

/** 分页查询操作审计日志 */
export function getAuditLogPage(params: {
  pageNum: number
  pageSize: number
  moduleName?: string
  username?: string
  keyword?: string
}) {
  return request.get('/audit-log/page', { params })
}
