import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import router from '@/router'
import type { Result } from '@/types'
import { clearTokens, getAccessToken, setTokens } from '@/utils/auth'
import { API_BASE_URL } from '@/utils/env'
import i18n from '@/locales'

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  // R4-1.47（批次1·P1-F3）：refresh token 已迁移 httpOnly Cookie，需携带凭证（同源反代无副作用；
  // dev 直连后端跨域时携带 cookie 依赖后端 CORS allowCredentials 已开启）
  withCredentials: true,
})

/**
 * R4-1.32：泛型化请求包装——类型契约的唯一入口。
 *
 * 响应拦截器已把 Result 成功分支解包为 data（业务码非 0 或网络异常则 reject），
 * 故这里把方法返回类型声明为 Promise<T>，由 api 层调用处显式传入期望的数据类型
 * （如 request.get<PageResult<UserVo>>('/system/user')），使"api 声明的返回类型"
 * 与"接口实际返回的数据形状"在编译期对齐；不传 <T> 时退化为 Promise<never>
 * （保持旧行为：由 api 函数自身的返回类型声明兜底契约）。
 * 拦截器内的 `as never` 与这里的 `as Promise<T>` 是仅有的两处类型逃生门，
 * 全部集中在 axios 边界，业务代码不再需要任何断言。
 */
const request = {
  get<T = never>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as Promise<T>
  },
  post<T = never>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as Promise<T>
  },
  put<T = never>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as Promise<T>
  },
  delete<T = never>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as Promise<T>
  },
}

export default request
export { service }

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
  // R4-1.47：refresh token 已迁移 httpOnly Cookie，不再从 localStorage 读取；
  // 无本地 access token 即视为未登录，直接失败
  if (!getAccessToken()) {
    return Promise.resolve(false)
  }
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function doRefresh(): Promise<boolean> {
  let failed = false
  try {
    // refresh token 由 httpOnly Cookie 自动携带（withCredentials），body 不再传
    const response = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
      `${service.defaults.baseURL}/auth/refresh`,
      {},
      { withCredentials: true },
    )
    if (response.data.code === 0) {
      setTokens(response.data.data.accessToken)
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
    // R4-1.33：主动取消（AbortController，如 useTableQuery 卸载/重发时 abort）
    // 不算业务错误——静默透传，不弹错误提示、不触发网络重试
    if (error.code === 'ERR_CANCELED' || error.__CANCEL__) {
      return Promise.reject(error)
    }
    // R4-1.47（批次1·P1-F1）：网络错误/超时仅对幂等方法自动重试——
    // 此前对 POST/PUT/DELETE 无条件重试一次，服务端已落库但响应丢失时会产生
    // 重复提交（重复用户/重复导入/重复 AI 任务）。写操作改由调用方自行决定重试策略。
    const method = String(error.config?.method || 'get').toUpperCase()
    const isIdempotent = ['GET', 'HEAD', 'OPTIONS'].includes(method)
    if (
      isIdempotent &&
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

function localizedMessage(code: number | undefined, fallback: string): string {
  if (code == null) {
    return fallback
  }
  const key = `error.${code}`
  const translated = i18n.global.t(key)
  return translated === key ? fallback : translated
}
