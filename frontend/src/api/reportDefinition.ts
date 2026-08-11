import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface ReportDefinitionVo {
  id: number
  name: string
  code: string
  category?: string
  dataSource: string
  chartType?: string
  paramsJson?: string
  remark?: string
  status: number
  /** 乐观锁版本号：编辑时需原样回传 */
  version?: number
  createdAt?: string
}

export interface ReportDefinitionSaveRequest {
  id?: number
  name: string
  code: string
  category?: string
  dataSource: string
  chartType?: string
  paramsJson?: string
  remark?: string
  status?: number
  version?: number
}

export interface ReportExecuteResultVo {
  columns: string[]
  rows: Record<string, unknown>[]
}

export function getReportDefinitionPage(params: Record<string, unknown>): Promise<PageResult<ReportDefinitionVo>> {
  return request.get('/report/definition/page', { params })
}

export function getReportDefinitionDetail(id: number): Promise<ReportDefinitionVo> {
  return request.get(`/report/definition/${id}`)
}

export function createReportDefinition(data: ReportDefinitionSaveRequest): Promise<number> {
  return request.post('/report/definition', data)
}

export function updateReportDefinition(data: ReportDefinitionSaveRequest): Promise<void> {
  return request.put('/report/definition', data)
}

export function deleteReportDefinition(id: number): Promise<void> {
  return request.delete(`/report/definition/${id}`)
}

/** 执行报表只读查询，返回 {columns, rows} 仅作展示 */
export function executeReportDefinition(id: number, params: Record<string, unknown> = {}): Promise<ReportExecuteResultVo> {
  return request.post(`/report/definition/${id}/execute`, { params })
}
