import request from './request'

export function uploadFile(flowId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/file/upload/${flowId}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getFilePage(params: { pageNum: number; pageSize: number; fileName?: string; flowId?: number }) {
  return request.get('/file/page', { params })
}

export function deleteFile(id: number) {
  return request.delete(`/file/${id}`)
}

export function getFileInfo(flowId: number) {
  return request.get(`/file/flow/${flowId}`)
}

export function getFileDownloadUrl(id: number): string {
  return `/api/file/download/${id}`
}

/** 触发浏览器下载（隐藏 <a> 方式，无页面导航/新标签闪烁；文件名由服务端 Content-Disposition 提供） */
export function downloadFileById(id: number) {
  const link = document.createElement('a')
  link.href = getFileDownloadUrl(id)
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
