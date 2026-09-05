import request from '@/utils/request'

export interface NameValueVo {
  name: string
  value: number
}

export interface RecentOperVo {
  userId?: number
  module?: string
  action?: string
  status?: number
  durationMs?: number
  operTime?: string
}

export interface ReportCenterVo {
  userCount: number
  roleCount: number
  deptCount: number
  deviceCount: number
  jobCount: number
  flowCount: number
  loginTrend: NameValueVo[]
  operByModule: NameValueVo[]
  deviceByType: NameValueVo[]
  deviceByStatus: NameValueVo[]
  jobByStatus: NameValueVo[]
  aiByStatus: NameValueVo[]
}

export interface ReportScreenVo {
  loginSuccessCount: number
  operTotal: number
  operErrorCount: number
  aiTaskCount: number
  loginTrend: NameValueVo[]
  operTrend: NameValueVo[]
  operByModule: NameValueVo[]
  deviceByType: NameValueVo[]
  deviceByStatus: NameValueVo[]
  jobByStatus: NameValueVo[]
  aiByStatus: NameValueVo[]
  recentOpers: RecentOperVo[]
}

export function getReportCenter(): Promise<ReportCenterVo> {
  return request.get('/report/center')
}

export function getReportScreen(): Promise<ReportScreenVo> {
  return request.get('/report/screen')
}

/** 数据大屏 SSE 订阅票据：登录态签发，60s 内有效。 */
export function getReportScreenTicket(): Promise<string> {
  return request.get('/report/screen/ticket')
}
