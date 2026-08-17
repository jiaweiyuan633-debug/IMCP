import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import type { LoginResponse } from '@/types'

const { loginApi, getMeApi, logoutApi, changePasswordApi, authMocks, permissionReset } = vi.hoisted(() => {
  const authMocks = {
    setTokens: vi.fn(),
    clearTokens: vi.fn(),
    getAccessToken: vi.fn(() => 'at'),
    getRefreshToken: vi.fn(() => ''),
  }
  return {
    loginApi: vi.fn(),
    getMeApi: vi.fn(),
    logoutApi: vi.fn(),
    changePasswordApi: vi.fn(),
    authMocks,
    permissionReset: vi.fn(),
  }
})

vi.mock('@/utils/auth', () => authMocks)
vi.mock('@/api/auth', () => ({
  login: loginApi,
  getMe: getMeApi,
  logout: logoutApi,
  changePassword: changePasswordApi,
}))
vi.mock('@/stores/permission', () => ({
  usePermissionStore: () => ({ reset: permissionReset }),
}))

/**
 * R4-1.53（批次7）：user store 契约测试——登录应用 token（refresh 走 cookie 不落
 * localStorage）、mustChangePassword 状态流转（首登强制改密）、登出/重置清理。
 * 覆盖批次1 的 refresh cookie 迁移与强制改密标记。
 */
describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    authMocks.getAccessToken.mockReturnValue('at')
  })

  function loginResponse(mustChangePassword = false): LoginResponse {
    return {
      accessToken: 'at2',
      refreshToken: 'rt2',
      mustChangePassword,
      user: {
        id: 1,
        username: 'admin',
        roles: ['admin'],
        perms: [],
        menus: [],
        mustChangePassword,
      },
    }
  }

  it('login 应用 accessToken 与用户信息（refresh token 不落 localStorage）', async () => {
    loginApi.mockResolvedValue(loginResponse())
    const store = useUserStore()

    await store.login({ username: 'admin', password: 'x' })

    // 批次1：setTokens 只持久化 access token（refresh token 由 httpOnly Cookie 管理）
    expect(authMocks.setTokens).toHaveBeenCalledWith('at2')
    expect(store.accessToken).toBe('at2')
    expect(store.userInfo?.username).toBe('admin')
    expect(store.mustChangePassword).toBe(false)
  })

  it('mustChangePassword 标记在登录响应/用户信息中透传', async () => {
    loginApi.mockResolvedValue(loginResponse(true))
    const store = useUserStore()

    await store.login({ username: 'admin', password: 'x' })

    expect(store.mustChangePassword).toBe(true)
  })

  it('fetchMe 同步必须改密标记', async () => {
    getMeApi.mockResolvedValue({ id: 1, username: 'u', roles: [], perms: [], menus: [], mustChangePassword: true })
    const store = useUserStore()

    await store.fetchMe()

    expect(store.mustChangePassword).toBe(true)
  })

  it('changePassword 成功后清除强制改密标记', async () => {
    changePasswordApi.mockResolvedValue(undefined)
    const store = useUserStore()
    store.mustChangePassword = true

    await store.changePassword({ oldPassword: 'old', newPassword: 'New123!abc' })

    expect(changePasswordApi).toHaveBeenCalledWith({ oldPassword: 'old', newPassword: 'New123!abc' })
    expect(store.mustChangePassword).toBe(false)
  })

  it('logout 清理凭证、用户信息与强制改密标记', async () => {
    logoutApi.mockResolvedValue(undefined)
    const store = useUserStore()
    store.accessToken = 'at'
    store.mustChangePassword = true
    store.userInfo = { id: 1, username: 'u', roles: [], perms: [], menus: [] }

    await store.logout()

    expect(authMocks.clearTokens).toHaveBeenCalled()
    expect(store.accessToken).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.mustChangePassword).toBe(false)
    expect(permissionReset).toHaveBeenCalled()
  })

  it('reset 清空全部状态', () => {
    const store = useUserStore()
    store.accessToken = 'at'
    store.mustChangePassword = true

    store.reset()

    expect(authMocks.clearTokens).toHaveBeenCalled()
    expect(store.accessToken).toBe('')
    expect(store.mustChangePassword).toBe(false)
    expect(permissionReset).toHaveBeenCalled()
  })
})
