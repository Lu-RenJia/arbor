import request from './request'

export interface TraceNode {
  id: string
  label: string
  type: string
  level: number
}

export interface TraceEdge {
  source: string
  target: string
}

export interface ResourceTraceVO {
  nodes: TraceNode[]
  edges: TraceEdge[]
  rootName: string
  rootType: string
}

/**
 * 追踪指定资源的引用链路
 * @param type 资源类型: global_param / datasource / db_script / program_script / api_config / redis_datasource / redis_script / flow
 * @param id 资源ID
 */
export function getResourceTrace(type: string, id: number | string) {
  return request.get<any, ResourceTraceVO>(`/resource-trace/${type}/${id}`)
}
