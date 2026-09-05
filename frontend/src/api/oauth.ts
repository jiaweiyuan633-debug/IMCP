import request from '@/utils/request'
import type { LoginResponse, PageResult } from '@/types'

export interface OauthProviderVo {
  provider: string
  label: string
  enabled: boolean
}

export interface OauthBindingVo {
  provider: string
  providerLabel: string
  openId: string
  nickname?: string
  avatar?: string
  createdAt?: string
}

export interface OauthConfigVo {
  id: number
  provider: string
  providerLabel: string
  appId: string
  appSecret: string
  redirectUri?: string
  scope?: string
  enabled: number
  sort: number
  remark?: string
  createdAt?: string
}

export interface OauthConfigSaveRequest {
  id?: number
  provider: string
  appId: string
  appSecret: string
  redirectUri?: string
  scope?: string
  enabled?: number
  sort?: number
  remark?: string
}

export interface OauthClientVo {
  id: number
  clientName: string
  clientId: string
  clientSecret: string
  redirectUri?: string
  scope?: string
  enabled: number
  sort: number
  remark?: string
  createdAt?: string
}

export interface OauthClientSaveRequest {
  id?: number
  clientName: string
  clientId: string
  clientSecret: string
  redirectUri?: string
  scope?: string
  enabled?: number
  sort?: number
  remark?: string
}

// ---------- 第三方登录 ----------

export function getOauthProviders(): Promise<OauthProviderVo[]> {
  return request.get('/auth/oauth/providers')
}

export function getOauthAuthorizeUrl(data: { provider: string; bindMode?: boolean }): Promise<{ url: string }> {
  return request.post('/auth/oauth/authorize-url', data)
}

export function consumeOauthTicket(ticket: string): Promise<LoginResponse> {
  return request.post('/auth/oauth/ticket', { ticket })
}

export function bindOauth(data: { bindToken: string; username: string; password: string }): Promise<LoginResponse> {
  return request.post('/auth/oauth/bind', data)
}

export function getOauthBindings(): Promise<OauthBindingVo[]> {
  return request.get('/auth/oauth/bindings')
}

export function unbindOauth(provider: string): Promise<void> {
  return request.post(`/auth/oauth/unbind/${provider}`)
}

// ---------- 第三方登录配置管理 ----------

export function getOauthConfigPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<OauthConfigVo>> {
  return request.get('/auth/oauth/config', { params, signal })
}

export function createOauthConfig(data: OauthConfigSaveRequest): Promise<number> {
  return request.post('/auth/oauth/config', data)
}

export function updateOauthConfig(data: OauthConfigSaveRequest): Promise<void> {
  return request.put('/auth/oauth/config', data)
}

export function updateOauthConfigStatus(id: number, enabled: number): Promise<void> {
  return request.put(`/auth/oauth/config/${id}/status`, { enabled })
}

export function deleteOauthConfig(id: number): Promise<void> {
  return request.delete(`/auth/oauth/config/${id}`)
}

// ---------- SSO 应用管理 ----------

export function getOauthClientPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<OauthClientVo>> {
  return request.get('/auth/oauth/client', { params, signal })
}

export function createOauthClient(data: OauthClientSaveRequest): Promise<number> {
  return request.post('/auth/oauth/client', data)
}

export function updateOauthClient(data: OauthClientSaveRequest): Promise<void> {
  return request.put('/auth/oauth/client', data)
}

export function updateOauthClientStatus(id: number, enabled: number): Promise<void> {
  return request.put(`/auth/oauth/client/${id}/status`, { enabled })
}

export function deleteOauthClient(id: number): Promise<void> {
  return request.delete(`/auth/oauth/client/${id}`)
}
