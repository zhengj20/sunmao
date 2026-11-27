import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('reip_token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

request.interceptors.response.use(
  resp => {
    const r = resp.data
    if (r && typeof r.code !== 'undefined') {
      if (r.code === 0) return r.data
      if (r.code === 401) {
        localStorage.removeItem('reip_token')
        if (location.pathname !== '/login') location.href = '/login'
        return Promise.reject(new Error('未登录'))
      }
      ElMessage.error(r.msg || '操作失败')
      return Promise.reject(new Error(r.msg || '操作失败'))
    }
    return r
  },
  err => {
    ElMessage.error(err.message || '网络异常')
    return Promise.reject(err)
  }
)

export default request
