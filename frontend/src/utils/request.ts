import axios from 'axios'
import { message } from 'ant-design-vue'
import router from '@/router'
import type { Result } from '@/types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 20000,
})

service.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing = false

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  if (refreshing) {
    await new Promise((resolve) => setTimeout(resolve, 300))
    return Boolean(getAccessToken())
  }
  refreshing = true
  try {
    const response = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
      `${service.defaults.baseURL}/api/auth/refresh`,
      { refreshToken },
    )
    if (response.data.code === 0) {
      setTokens(response.data.data.accessToken, response.data.data.refreshToken)
      return true
    }
    clearTokens()
    router.push('/login')
    return false
  } catch {
    clearTokens()
    router.push('/login')
    return false
  } finally {
    refreshing = false
  }
}

service.interceptors.response.use(
  async (response) => {
    const result = response.data as Result<unknown>
    if (result.code === 0) {
      return result.data as never
    }
    if (result.code === 401 && !response.config.retried) {
      const ok = await refreshAccessToken()
      if (ok) {
        response.config.retried = true
        response.config.headers.Authorization = `Bearer ${getAccessToken()}`
        return service.request(response.config)
      }
    }
    message.error(result.message || '请求失败')
    return Promise.reject(new Error(result.message))
  },
  async (error) => {
    if (error.response?.status === 401 && !error.config?.retried) {
      const ok = await refreshAccessToken()
      if (ok) {
        error.config.retried = true
        error.config.headers.Authorization = `Bearer ${getAccessToken()}`
        return service.request(error.config)
      }
    }
    message.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default service

