import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * R4-1.32：request 拦截器与泛型包装契约测试。
 *
 * 通过 vi.mock 构造一个可编程的 axios 实例，捕获请求/响应拦截器处理器后直接驱动，
 * 覆盖：成功解包、blob 原样返回、业务码错误 reject、401 刷新重试、刷新失败跳登录、
 * 网络错误自动重试、请求拦截器附加 token、泛型包装方法转发。
 */

// 可编程 axios 实例 + 捕获的拦截器处理器
const { requestHandlers, responseHandlers, errorHandlers, instance, axiosPost, authMocks, routerPush, messageError } =
  vi.hoisted(() => {
    const requestHandlers: Array<(config: Record<string, unknown>) => unknown> = []
    const responseHandlers: Array<(response: unknown) => unknown> = []
    const errorHandlers: Array<(error: unknown) => unknown> = []
    const instance = {
      defaults: { baseURL: '/api' },
      interceptors: {
        request: { use: (handler: (c: Record<string, unknown>) => unknown) => requestHandlers.push(handler) },
        response: {
          use: (onOk: (r: unknown) => unknown, onErr: (e: unknown) => unknown) => {
            responseHandlers.push(onOk)
            errorHandlers.push(onErr)
          },
        },
      },
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
      request: vi.fn(),
    }
    const axiosPost = vi.fn()
    const authMocks = {
      getAccessToken: vi.fn(() => 'at'),
      getRefreshToken: vi.fn(() => 'rt'),
      setTokens: vi.fn(),
      clearTokens: vi.fn(),
    }
    const routerPush = vi.fn()
    const messageError = vi.fn()
    return { requestHandlers, responseHandlers, errorHandlers, instance, axiosPost, authMocks, routerPush, messageError }
  })

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => instance),
    post: axiosPost,
  },
}))

vi.mock('ant-design-vue', () => ({
  message: { error: messageError },
}))

vi.mock('@/router', () => ({
  default: { push: routerPush },
}))

vi.mock('@/utils/auth', () => authMocks)

vi.mock('@/utils/env', () => ({
  API_BASE_URL: '/api',
}))

vi.mock('@/locales', () => ({
  default: { global: { t: (key: string) => key } },
}))

// 在 mock 就绪后加载被测模块，触发 axios.create 与拦截器注册
import request from '@/utils/request'

const onRequest = requestHandlers[0] as (config: Record<string, unknown>) => unknown
const onFulfilled = responseHandlers[0] as (response: {
  data: unknown
  config: { responseType?: string; retried?: boolean; headers?: Record<string, unknown> }
}) => Promise<unknown>
const onRejected = errorHandlers[0] as (error: unknown) => Promise<unknown>

describe('request 拦截器', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authMocks.getAccessToken.mockReturnValue('at')
    authMocks.getRefreshToken.mockReturnValue('rt')
  })

  it('成功响应解包为 Result.data', async () => {
    const result = await onFulfilled({ data: { code: 0, data: { id: 1 } }, config: {} })

    expect(result).toEqual({ id: 1 })
    expect(messageError).not.toHaveBeenCalled()
  })

  it('blob 响应原样返回整个 AxiosResponse（含 headers）', async () => {
    const blob = new Blob(['x'], { type: 'text/csv' })
    const response = {
      data: blob,
      config: { responseType: 'blob' },
      headers: { 'content-disposition': 'attachment; filename=a.csv' },
    }

    const result = await onFulfilled(response)

    expect(result).toBe(response)
  })

  it('业务码非 0 时 reject（Error.code 透出业务码）并展示错误消息', async () => {
    await expect(
      onFulfilled({ data: { code: 500, message: '服务端错误' }, config: {} }),
    ).rejects.toMatchObject({ code: 500, message: '服务端错误' })
    expect(messageError).toHaveBeenCalled()
  })

  it('401 时刷新 token 并重放原请求（retried=true）', async () => {
    axiosPost.mockResolvedValue({
      data: { code: 0, data: { accessToken: 'at2', refreshToken: 'rt2' } },
    })
    instance.request.mockResolvedValue('重试后的数据')

    const result = await onFulfilled({
      data: { code: 401, message: '未认证' },
      config: { retried: false, headers: {} },
    })

    expect(authMocks.setTokens).toHaveBeenCalledWith('at2', 'rt2')
    expect(instance.request).toHaveBeenCalledWith(
      expect.objectContaining({ retried: true, headers: { Authorization: 'Bearer at' } }),
    )
    expect(result).toBe('重试后的数据')
  })

  it('401 且刷新失败时清除 token 并跳转登录页', async () => {
    axiosPost.mockResolvedValue({ data: { code: 401, message: 'refresh 过期' } })

    await expect(
      onFulfilled({ data: { code: 401, message: '未认证' }, config: { retried: false, headers: {} } }),
    ).rejects.toMatchObject({ code: 401 })

    expect(authMocks.clearTokens).toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith('/login')
  })

  it('无 refreshToken 时直接 reject 且不跳转', async () => {
    authMocks.getRefreshToken.mockReturnValue('')

    await expect(
      onFulfilled({ data: { code: 401, message: '未认证' }, config: { retried: false, headers: {} } }),
    ).rejects.toMatchObject({ code: 401 })
    expect(authMocks.clearTokens).not.toHaveBeenCalled()
    expect(routerPush).not.toHaveBeenCalled()
  })

  it('网络错误自动重试一次', async () => {
    instance.request.mockResolvedValue('网络重试后的数据')
    const error = { code: 'ERR_NETWORK', config: { retried: false, headers: {} }, message: 'Network Error' }

    vi.useFakeTimers()
    const promise = onRejected(error)
    await vi.advanceTimersByTimeAsync(600)
    const result = await promise
    vi.useRealTimers()

    expect(instance.request).toHaveBeenCalledWith(expect.objectContaining({ retried: true }))
    expect(result).toBe('网络重试后的数据')
  })

  it('错误分支 401 刷新成功后重放请求', async () => {
    axiosPost.mockResolvedValue({
      data: { code: 0, data: { accessToken: 'at2', refreshToken: 'rt2' } },
    })
    instance.request.mockResolvedValue('重试后的数据')
    const error = {
      response: { status: 401, data: { code: 401, message: '未认证' } },
      config: { retried: false, headers: {} },
      message: 'Request failed with status code 401',
    }

    const result = await onRejected(error)

    expect(instance.request).toHaveBeenCalledWith(expect.objectContaining({ retried: true }))
    expect(result).toBe('重试后的数据')
  })

  it('普通 HTTP 错误展示服务端消息并 reject 原错误', async () => {
    const error = {
      response: { status: 500, data: { code: 500, message: '服务端错误' } },
      config: { retried: true },
      message: 'Request failed with status code 500',
    }

    await expect(onRejected(error)).rejects.toBe(error)
    expect(messageError).toHaveBeenCalled()
  })

  it('请求拦截器为带 token 的请求附加 Authorization', () => {
    const config = { headers: {} }
    const out = onRequest(config) as { headers: Record<string, string> }

    expect(out.headers.Authorization).toBe('Bearer at')
  })
})

describe('request 泛型包装', () => {
  it('get/post/put/delete 转发到 axios 实例并保留解包后的数据', async () => {
    instance.get.mockResolvedValue({ id: 1 })
    instance.post.mockResolvedValue(2)
    instance.put.mockResolvedValue(undefined)
    instance.delete.mockResolvedValue(undefined)

    await expect(request.get<{ id: number }>('/a')).resolves.toEqual({ id: 1 })
    await expect(request.post<number>('/b', { x: 1 })).resolves.toBe(2)
    await expect(request.put('/c', { x: 1 })).resolves.toBeUndefined()
    await expect(request.delete('/d')).resolves.toBeUndefined()

    expect(instance.get).toHaveBeenCalledWith('/a', undefined)
    expect(instance.post).toHaveBeenCalledWith('/b', { x: 1 }, undefined)
    expect(instance.put).toHaveBeenCalledWith('/c', { x: 1 }, undefined)
    expect(instance.delete).toHaveBeenCalledWith('/d', undefined)
  })
})
