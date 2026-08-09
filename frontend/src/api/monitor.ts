import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface LoginLogVo {
  id: number
  username: string
  ip?: string
  userAgent?: string
  status: number
  message?: string
  loginTime?: string
}

export interface OperLogVo {
  id: number
  userId?: number
  module?: string
  action?: string
  method?: string
  requestUrl?: string
  requestMethod?: string
  params?: string
  result?: string
  status: number
  errorMsg?: string
  ip?: string
  durationMs?: number
  operTime?: string
}

export interface OnlineUserVo {
  tokenId: string
  userId: number
  username: string
  ip?: string
  userAgent?: string
  loginTime?: string
}

export interface DashboardStatsVo {
  userCount: number
  roleCount: number
  menuCount: number
  loginLogCount: number
  operLogCount: number
  aiTaskTotal: number
  aiTaskSucceeded: number
  aiTaskFailed: number
  aiTaskRunning: number
}

export function getLoginLogPage(params: Record<string, unknown>): Promise<PageResult<LoginLogVo>> {
  return request.get('/monitor/login-log', { params })
}

export function getOperLogPage(params: Record<string, unknown>): Promise<PageResult<OperLogVo>> {
  return request.get('/monitor/oper-log', { params })
}

export function getOnlineUsers(): Promise<OnlineUserVo[]> {
  return request.get('/monitor/online')
}

export function kickOnlineUser(tokenId: string): Promise<void> {
  return request.delete(`/monitor/online/${tokenId}`)
}

export function clearCacheKey(key: string): Promise<void> {
  return request.delete(`/monitor/cache/${encodeURIComponent(key)}`)
}

export function getDashboardStats(): Promise<DashboardStatsVo> {
  return request.get('/monitor/stats')
}

export interface JobVo {
  id: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  misfirePolicy?: string
  concurrent: number
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export function getJobPage(params: Record<string, unknown>): Promise<PageResult<JobVo>> {
  return request.get('/monitor/job', { params })
}

export function createJob(data: Record<string, unknown>): Promise<number> {
  return request.post('/monitor/job', data)
}

export function updateJob(data: Record<string, unknown>): Promise<void> {
  return request.put('/monitor/job', data)
}

export function deleteJob(id: number): Promise<void> {
  return request.delete(`/monitor/job/${id}`)
}

export function changeJobStatus(id: number, status: number): Promise<void> {
  return request.put(`/monitor/job/${id}/status`, { status })
}

export function runJob(id: number): Promise<void> {
  return request.post(`/monitor/job/${id}/run`)
}

export interface JobLogVo {
  id: number
  jobName?: string
  jobGroup?: string
  invokeTarget?: string
  jobMessage?: string
  status: number
  exceptionInfo?: string
  startTime?: string
  endTime?: string
}

export function getJobLogPage(params: Record<string, unknown>): Promise<PageResult<JobLogVo>> {
  return request.get('/monitor/job/log', { params })
}

export interface ServerMonitorVo {
  osName: string
  osArch: string
  hostName: string
  cpuCores: number
  cpuLoad: number
  memTotal: number
  memUsed: number
  memUsagePercent: number
  jvmMax: number
  jvmUsed: number
  jvmUsagePercent: number
  uptimeSeconds: number
  disks: { name: string; total: number; used: number; usagePercent: number }[]
}

export function getServerMonitor(): Promise<ServerMonitorVo> {
  return request.get('/monitor/server')
}

export interface SqlLogVo {
  id: number
  sqlText?: string
  method?: string
  durationMs?: number
  success: number
  errorMsg?: string
  createdAt?: string
}

export function getSqlLogPage(params: Record<string, unknown>): Promise<PageResult<SqlLogVo>> {
  return request.get('/monitor/sql-log', { params })
}

export interface AlertRuleVo {
  id: number
  ruleName: string
  metric: string
  operator: string
  threshold: number
  enabled: number
  severity?: string
  silenceMinutes?: number
  webhookUrl?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export function getAlertRulePage(params: Record<string, unknown>): Promise<PageResult<AlertRuleVo>> {
  return request.get('/monitor/alert-rule', { params })
}

export function createAlertRule(data: Record<string, unknown>): Promise<number> {
  return request.post('/monitor/alert-rule', data)
}

export function updateAlertRule(data: Record<string, unknown>): Promise<void> {
  return request.put('/monitor/alert-rule', data)
}

export function deleteAlertRule(id: number): Promise<void> {
  return request.delete(`/monitor/alert-rule/${id}`)
}

export function runAlertRuleCheck(): Promise<number> {
  return request.post('/monitor/alert-rule/run')
}

export interface AuditLogVo {
  id: number
  userId?: number
  module?: string
  action?: string
  params?: string
  result?: string
  status: number
  createdAt?: string
}

export function getAuditLogPage(params: Record<string, unknown>): Promise<PageResult<AuditLogVo>> {
  return request.get('/monitor/audit-log', { params })
}

export async function exportAuditLogs(): Promise<void> {
  const data = (await request.get('/monitor/audit-log/export', { responseType: 'blob' })) as unknown as Blob
  const url = URL.createObjectURL(data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'audit-log.csv'
  link.click()
  URL.revokeObjectURL(url)
}


