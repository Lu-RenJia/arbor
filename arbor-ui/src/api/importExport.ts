import request from './request'

/** 导出流程（返回导入包 JSON 字符串） */
export function exportFlows(flowIds: number[]) {
  return request.post('/import-export/export-flows', { flowIds }, { timeout: 0 })
}

/** 导出应用（返回导入包 JSON 字符串） */
export function exportApps(appIds: number[]) {
  return request.post('/import-export/export-apps', { appIds }, { timeout: 0 })
}

/** 导入预览：解析包并比对目标环境，返回冲突清单与缺失项 */
export function importPreview(packageJson: string) {
  return request.post('/import-export/preview', { packageJson }, { timeout: 0 })
}

/** 导入执行：defaultConflictAction=SKIP/OVERWRITE，overrides 为单项覆盖（uid→动作） */
export function importExecute(packageJson: string, defaultConflictAction: string, overrides?: Record<string, string>) {
  return request.post('/import-export/execute', { packageJson, defaultConflictAction, overrides }, { timeout: 0 })
}
