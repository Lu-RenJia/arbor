import request from './request'

/** 查询指定流程的所有内联资源 */
export function getFlowInlineResources(flowId: number) {
  return request.get(`/inline-resource/flow/${flowId}`)
}

/** 内联→全局：解除 flowId 绑定 */
export function promoteResource(type: string, id: number) {
  return request.put(`/inline-resource/promote/${type}/${id}`)
}

/** 全局→内联：复制一份绑定到指定流程 */
export function demoteResource(type: string, id: number, flowId: number) {
  return request.post(`/inline-resource/demote/${type}/${id}`, { flowId })
}
