import request from '@/utils/request'
import type { CaptchaResponse, LoginConfigVo, LoginForm, LoginResponse, UserInfo } from '@/types'

// 所有调用显式携带 <T>，与函数声明的返回类型对齐——
// 泛型化 request 使"接口实际数据形状"与"api 声明的返回类型"在编译期绑定。
export function login(data: LoginForm): Promise<LoginResponse> {
  return request.post<LoginResponse>('/auth/login', data)
}

export function getMe(): Promise<UserInfo> {
  return request.get<UserInfo>('/auth/me')
}

export function logout(): Promise<void> {
  return request.post<void>('/auth/logout')
}

export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return request.put<void>('/auth/password', data)
}

export function updateProfile(data: {
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
}): Promise<void> {
  return request.put<void>('/auth/profile', data)
}

export function getLoginConfig(): Promise<LoginConfigVo> {
  return request.get<LoginConfigVo>('/auth/login-config')
}

export function getCaptcha(): Promise<CaptchaResponse> {
  return request.get<CaptchaResponse>('/auth/captcha')
}

export interface TotpStatusVo {
  enabled: boolean
  secret?: string
  otpauthUrl?: string
}

export function getTotpStatus(): Promise<TotpStatusVo> {
  return request.get<TotpStatusVo>('/auth/totp/status')
}

export function setupTotp(): Promise<TotpStatusVo> {
  return request.post<TotpStatusVo>('/auth/totp/setup')
}

export function enableTotp(code: string): Promise<void> {
  return request.post<void>('/auth/totp/enable', { code })
}

export function disableTotp(code: string): Promise<void> {
  return request.post<void>('/auth/totp/disable', { code })
}

