import axios from 'axios'
import { createDiscreteApi } from 'naive-ui'

const { message } = createDiscreteApi(['message'])

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    // blob 响应（文件下载等）直接透传，不做 JSON 解析
    if (res.config.responseType === 'blob' || res.data instanceof Blob) {
      return res.data
    }
    const data = res.data
    if (data.code !== 200) {
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data.data
  },
  (err) => {
    // 主动取消（AbortController.abort，如取消流程执行）：不弹错误提示，由调用方自行处理
    if (err.code === 'ERR_CANCELED') {
      return Promise.reject(err)
    }
    const status = err.response?.status
    // 401 未授权 / 403 禁止访问 → token 失效，跳转登录
    if (status === 401 || status === 403) {
      localStorage.removeItem('token')
      window.location.hash = '#/login'
      window.location.reload()
    }
    // 优先提取后端返回的消息
    const backendMsg = err.response?.data?.message
    const msg = backendMsg || err.message || '网络错误'
    message.error(msg)
    return Promise.reject(err)
  }
)

export default request
