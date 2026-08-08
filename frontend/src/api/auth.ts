import request from '@/utils/request'
import type { LoginForm, LoginResponse, UserInfo } from '@/types'

export function login(data: LoginForm): Promise<LoginResponse> {
  return request.post('/api/auth/login', data)
}

export function getMe(): Promise<UserInfo> {
  return request.get('/api/auth/me')
}

export function logout(): Promise<void> {
  return request.post('/api/auth/logout')
}

export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return request.put('/api/auth/password', data)
}

