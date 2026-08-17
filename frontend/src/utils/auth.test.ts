import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  initAuthSync,
  setTokens,
  subscribeAuthCleared,
} from '@/utils/auth'

/**
 * R4-1.33 + R4-1.47（批次1）：凭证存储契约测试。
 * 覆盖本地读写 + 跨标签页同步（storage 事件驱动的登出广播 / token 刷新）。
 * storage 事件仅在「其他标签页」触发，测试通过手动 dispatch 模拟。
 *
 * R4-1.47 变更：refresh token 已迁移后端 httpOnly Cookie，前端不再持久化——
 * setTokens 仅存 access token；getRefreshToken 恒返回空串。
 */
describe('auth token storage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores and reads access token (refresh token no longer persisted)', () => {
    setTokens('access-1')
    expect(getAccessToken()).toBe('access-1')
    expect(getRefreshToken()).toBe('')
  })

  it('clears tokens', () => {
    setTokens('access-1')
    clearTokens()
    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })
})

describe('auth 跨标签页同步', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function dispatchStorage(key: string, newValue: string | null) {
    window.dispatchEvent(new StorageEvent('storage', { key, newValue }))
  }

  it('其他标签页登出广播后同步清空内存并通知订阅者', () => {
    setTokens('at')
    initAuthSync()
    const listener = vi.fn()
    subscribeAuthCleared(listener)

    dispatchStorage('admin_auth_cleared_at', String(Date.now()))

    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('其他标签页直接清除 token 时同步清空', () => {
    setTokens('at')
    initAuthSync()

    // 模拟其他标签页清除凭证：localStorage 同源共享，此处先删共享存储再派发事件
    localStorage.removeItem('admin_access_token')
    dispatchStorage('admin_access_token', null)

    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })

  it('其他标签页刷新 token 时同步内存', () => {
    setTokens('at')
    initAuthSync()

    localStorage.setItem('admin_access_token', 'at2')
    dispatchStorage('admin_access_token', 'at2')

    expect(getAccessToken()).toBe('at2')
  })

  it('subscribeAuthCleared 返回取消订阅函数', () => {
    setTokens('at')
    initAuthSync()
    const listener = vi.fn()
    const unsubscribe = subscribeAuthCleared(listener)
    unsubscribe()

    dispatchStorage('admin_auth_cleared_at', String(Date.now()))

    expect(listener).not.toHaveBeenCalled()
  })

  it('initAuthSync 幂等：重复调用不导致重复通知', () => {
    setTokens('at')
    initAuthSync()
    initAuthSync()
    initAuthSync()
    const listener = vi.fn()
    subscribeAuthCleared(listener)

    dispatchStorage('admin_auth_cleared_at', String(Date.now()))

    expect(listener).toHaveBeenCalledTimes(1)
  })
})
