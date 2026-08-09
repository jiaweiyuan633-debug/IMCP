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

export function getLoginConfig(): Promise<LoginConfigVo> {
  return request.get('/auth/login-config')
}

export function getCaptcha(): Promise<CaptchaResponse> {
  return request.get('/auth/captcha')
}

