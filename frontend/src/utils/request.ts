import axios from 'axios'
import { message } from 'ant-design-vue'
import router from '@/router'
import type { Result } from '@/types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'
import { API_BASE_URL } from '@/utils/env'
import i18n from '@/locales'

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
})

service.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 共享同一个刷新 Promise：并发 401 只触发一次刷新，
// 其余请求等待同一结果，避免旧 token 清除前的轮询竞态导致反复刷新/死循环
let refreshPromise: Promise<boolean> | null = null

function refreshAccessToken(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return Promise.resolve(false)
  }
  if (!refreshPromise) {
    refreshPromise = doRefresh(refreshToken).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function doRefresh(refreshToken: string): Promise<boolean> {
  let failed = false
  try {
    const response = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
      `${service.defaults.baseURL}/auth/refresh`,
      { refreshToken },
    )
    if (response.data.code === 0) {
      setTokens(response.data.data.accessToken, response.data.data.refreshToken)
      return true
    }
    failed = true
  } catch {
    failed = true
  }
  if (failed) {
    clearTokens()
    router.push('/login')
  }
  return false
}

service.interceptors.response.use(
  async (response) => {
    // Blob（文件下载）响应不经过 Result 包装解析，直接返回完整 response
    // （否则 response.data 是 Blob，result.code === 0 恒为 false，下载会被误判为业务错误）
    if (response.config.responseType === 'blob') {
      return response
    }
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
    message.error(localizedMessage(result.code, result.message))
    const error = new Error(result.message) as Error & { code?: number }
    error.code = result.code
    return Promise.reject(error)
  },
  async (error) => {
    if (
      ['ECONNABORTED', 'ERR_NETWORK'].includes(error.code) &&
      !error.config?.retried
    ) {
      error.config.retried = true
      await new Promise((resolve) => setTimeout(resolve, 500))
      return service.request(error.config)
    }
    if (error.response?.status === 401 && !error.config?.retried) {
      const ok = await refreshAccessToken()
      if (ok) {
        error.config.retried = true
        error.config.headers.Authorization = `Bearer ${getAccessToken()}`
        return service.request(error.config)
      }
    }
    message.error(localizedMessage(error.response?.data?.code, error.response?.data?.message || error.message || i18n.global.t('common.networkError')))
    return Promise.reject(error)
  },
)

export default service

function localizedMessage(code: number | undefined, fallback: string): string {
  if (code == null) {
    return fallback
  }
  const key = `error.${code}`
  const translated = i18n.global.t(key)
  return translated === key ? fallback : translated
}

