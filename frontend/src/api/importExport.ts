import request from '@/utils/request'
import type { PageResult } from '@/types'

// ==================== 导入导出模板 ====================

export interface ImportExportTemplateVo {
  id: number
  name: string
  code: string
  /** import / export */
  type: string
  entityKey: string
  /** 列映射配置 JSON：{columns:[{key,header,required,dataType}],sheetName} */
  configJson: string
  remark?: string
  status: number
  /** 乐观锁版本号：编辑时需原样回传 */
  version?: number
  createdAt?: string
}

export interface ImportExportTemplateSaveRequest {
  id?: number
  name: string
  code: string
  type: string
  entityKey: string
  configJson: string
  remark?: string
  status?: number
  version?: number
}

export function getImportTemplatePage(params: Record<string, unknown>): Promise<PageResult<ImportExportTemplateVo>> {
  return request.get('/import-export/template/page', { params })
}

export function createImportTemplate(data: ImportExportTemplateSaveRequest): Promise<number> {
  return request.post('/import-export/template', data)
}

export function updateImportTemplate(data: ImportExportTemplateSaveRequest): Promise<void> {
  return request.put('/import-export/template', data)
}

export function deleteImportTemplate(id: number): Promise<void> {
  return request.delete(`/import-export/template/${id}`)
}

// ==================== 导入导出任务 ====================

export interface ImportExportJobVo {
  id: number
  templateId?: number
  templateCode: string
  /** import / export */
  type: string
  /** PENDING / SUCCEEDED / FAILED */
  status: string
  fileId?: number
  fileName?: string
  resultFileId?: number
  total?: number
  success?: number
  failed?: number
  errorMessage?: string
  createdAt?: string
}

export interface JobCreateRequest {
  /** 业务单号：仅作幂等键 */
  bizNo: string
  templateCode: string
  /** 导入：上传后的源文件 id（sys_file.id） */
  fileId?: number
  fileName?: string
  /** 导出：筛选参数 */
  query?: Record<string, unknown>
}

export interface DownloadVo {
  url: string
  fileName: string
}

export function getImportJobPage(params: Record<string, unknown>): Promise<PageResult<ImportExportJobVo>> {
  return request.get('/import-export/job/page', { params })
}

export function createImportJob(data: JobCreateRequest): Promise<number> {
  return request.post('/import-export/job/import', data)
}

export function createExportJob(data: JobCreateRequest): Promise<number> {
  return request.post('/import-export/job/export', data)
}

export function getImportJobDetail(id: number): Promise<ImportExportJobVo> {
  return request.get(`/import-export/job/${id}`)
}

export function getImportJobDownload(id: number): Promise<DownloadVo> {
  return request.get(`/import-export/job/${id}/download`)
}
