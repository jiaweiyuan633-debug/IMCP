import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * auth API 契约测试——锁定请求方法、URL 与载荷形状。
 * 泛型化的 request 使"api 声明的返回类型"与实际数据形状在编译期绑定，
 * 测试在此之上断言路径/参数不漂移。
 */

const { requestMock } = vi.hoisted(() => ({
  requestMock: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

vi.mock('@/utils/request', () => ({ default: requestMock }))

import {
  changePassword,
  disableTotp,
  enableTotp,
  getCaptcha,
  getLoginConfig,
  getMe,
  getTotpStatus,
  login,
  logout,
  setupTotp,
  updateProfile,
} from '@/api/auth'

describe('auth API 契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('login 提交登录表单', async () => {
    const form = { username: 'admin', password: 'secret', captchaId: 'c1', captchaCode: 'ab12' }
    requestMock.post.mockResolvedValue({
      accessToken: 'at',
      refreshToken: 'rt',
      user: { id: 1, username: 'admin', roles: [], perms: [], menus: [] },
    })

    const result = await login(form)

    expect(requestMock.post).toHaveBeenCalledWith('/auth/login', form)
    expect(result.accessToken).toBe('at')
  })

  it('getMe 拉取当前用户信息', async () => {
    requestMock.get.mockResolvedValue({ id: 1, username: 'admin', roles: [], perms: [], menus: [] })

    const result = await getMe()

    expect(requestMock.get).toHaveBeenCalledWith('/auth/me')
    expect(result.username).toBe('admin')
  })

  it('logout 登出', async () => {
    await logout()

    expect(requestMock.post).toHaveBeenCalledWith('/auth/logout')
  })

  it('changePassword 携带新旧密码', async () => {
    await changePassword({ oldPassword: 'old', newPassword: 'new' })

    expect(requestMock.put).toHaveBeenCalledWith('/auth/password', { oldPassword: 'old', newPassword: 'new' })
  })

  it('updateProfile 提交可编辑资料字段', async () => {
    await updateProfile({ nickname: 'N', avatar: 'a.png', email: 'e@x.com', phone: '138' })

    expect(requestMock.put).toHaveBeenCalledWith('/auth/profile', {
      nickname: 'N',
      avatar: 'a.png',
      email: 'e@x.com',
      phone: '138',
    })
  })

  it('getLoginConfig 拉取登录配置', async () => {
    requestMock.get.mockResolvedValue({ captchaEnabled: true })

    const result = await getLoginConfig()

    expect(requestMock.get).toHaveBeenCalledWith('/auth/login-config')
    expect(result.captchaEnabled).toBe(true)
  })

  it('getCaptcha 拉取验证码', async () => {
    requestMock.get.mockResolvedValue({ captchaId: 'c1', image: 'data:image/png;base64,xx' })

    const result = await getCaptcha()

    expect(requestMock.get).toHaveBeenCalledWith('/auth/captcha')
    expect(result.captchaId).toBe('c1')
  })

  it('TOTP 状态查询与绑定流程', async () => {
    requestMock.get.mockResolvedValue({ enabled: false })

    await getTotpStatus()
    expect(requestMock.get).toHaveBeenCalledWith('/auth/totp/status')

    requestMock.post.mockResolvedValue({ enabled: false, secret: 'ABC' })
    const setup = await setupTotp()
    expect(requestMock.post).toHaveBeenCalledWith('/auth/totp/setup')
    expect(setup.secret).toBe('ABC')

    await enableTotp('123456')
    expect(requestMock.post).toHaveBeenCalledWith('/auth/totp/enable', { code: '123456' })

    await disableTotp('654321')
    expect(requestMock.post).toHaveBeenCalledWith('/auth/totp/disable', { code: '654321' })
  })
})
