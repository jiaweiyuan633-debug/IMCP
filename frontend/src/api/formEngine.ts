import request from '@/utils/request'
import type { PageResult } from '@/types'

// ==================== 表单定义 ====================

/** 与 schema_json 数组中的单个元素一一对应 */
export interface FormField {
  key: string
  label: string
  /** input / textarea / number / date / select / multi-select / switch */
  type: string
  required?: boolean
  options?: string[]
  placeholder?: string
  maxLength?: number
}

export interface FormDefinitionVo {
  id: number
  name: string
  code: string
  description?: string
  /** 0 草稿 1 已发布 */
  status: number
  /** 乐观锁版本号：编辑时需原样回传 */
  version?: number
  schemaJson?: string
  layoutJson?: string
  createdAt?: string
  updatedAt?: string
}

export interface FormDefinitionSaveRequest {
  id?: number
  name: string
  code: string
  description?: string
  schemaJson: string
  layoutJson?: string
  version?: number
}

/** 已发布表单的渲染结构 */
export interface FormSchemaVo {
  fields: FormField[]
  /** layout_json 原样透传，如 {"columns":2} */
  layout: string
}

export function getFormDefinitionPage(params: Record<string, unknown>): Promise<PageResult<FormDefinitionVo>> {
  return request.get('/form/definition/page', { params })
}

export function getFormDefinitionDetail(id: number): Promise<FormDefinitionVo> {
  return request.get(`/form/definition/${id}`)
}

export function getFormSchema(id: number): Promise<FormSchemaVo> {
  return request.get(`/form/definition/${id}/schema`)
}

export function createFormDefinition(data: FormDefinitionSaveRequest): Promise<number> {
  return request.post('/form/definition', data)
}

export function updateFormDefinition(data: FormDefinitionSaveRequest): Promise<void> {
  return request.put('/form/definition', data)
}

export function publishFormDefinition(id: number): Promise<void> {
  return request.put(`/form/definition/${id}/publish`)
}

export function deleteFormDefinition(id: number): Promise<void> {
  return request.delete(`/form/definition/${id}`)
}

// ==================== 表单实例 ====================

export interface FormInstanceVo {
  id: number
  formId?: number
  formCode: string
  /** 提交数据（data_json 反序列化结果） */
  data: Record<string, unknown>
  /** SUBMITTED / APPROVED / REJECTED */
  status: string
  submitterId?: number
  submittedAt?: string
  remark?: string
  createdAt?: string
}

export interface FormInstanceSubmitRequest {
  /** 业务流水号：幂等键 */
  bizNo: string
  formCode: string
  /** 提交数据：key=字段 key，未知 key 忽略 */
  data?: Record<string, unknown>
}

export function submitFormInstance(data: FormInstanceSubmitRequest): Promise<number> {
  return request.post('/form/instance/submit', data)
}

export function getFormInstancePage(params: Record<string, unknown>): Promise<PageResult<FormInstanceVo>> {
  return request.get('/form/instance/page', { params })
}

export function getFormInstanceDetail(id: number): Promise<FormInstanceVo> {
  return request.get(`/form/instance/${id}`)
}

/** 审批流转：status 为 APPROVED / REJECTED */
export function approveFormInstance(id: number, status: string): Promise<void> {
  return request.put(`/form/instance/${id}/status`, { status })
}
