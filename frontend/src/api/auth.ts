import request from '@/utils/request'
import type { CaptchaResponse, LoginConfigVo, LoginForm, LoginResponse, UserInfo } from '@/types'

export function login(data: LoginForm): Promise<LoginResponse> {
  return request.post('/auth/login', data)
}

export function getMe(): Promise<UserInfo> {
  return request.get('/auth/me')
}

export function logout(): Promise<void> {
  return request.post('/auth/logout')
}

export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return request.put('/auth/password', data)
}

export function updateProfile(data: {
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
}): Promise<void> {
  return request.put('/auth/profile', data)
}

export function getLoginConfig(): Promise<LoginConfigVo> {
  return request.get('/auth/login-config')
}

export function getCaptcha(): Promise<CaptchaResponse> {
  return request.get('/auth/captcha')
}

export interface TotpStatusVo {
  enabled: boolean
  secret?: string
  otpauthUrl?: string
}

export function getTotpStatus(): Promise<TotpStatusVo> {
  return request.get('/auth/totp/status')
}

export function setupTotp(): Promise<TotpStatusVo> {
  return request.post('/auth/totp/setup')
}

export function enableTotp(code: string): Promise<void> {
  return request.post('/auth/totp/enable', { code })
}

export function disableTotp(code: string): Promise<void> {
  return request.post('/auth/totp/disable', { code })
}

