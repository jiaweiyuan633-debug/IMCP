import request from '@/utils/request'

export interface ScreenTemplate {
  id: number
  name: string
  code: string
  category?: string
  theme: string
  layout: string
  remark?: string
  builtin: boolean
  createdAt?: string
}

export interface ScreenTemplateSaveRequest {
  id?: number
  name: string
  code?: string
  category?: string
  theme?: string
  layout: string
  remark?: string
}

export function listScreenTemplates(): Promise<ScreenTemplate[]> {
  return request.get('/report/screen/template')
}

export function getScreenTemplate(id: number): Promise<ScreenTemplate> {
  return request.get(`/report/screen/template/${id}`)
}

export function createScreenTemplate(data: ScreenTemplateSaveRequest): Promise<number> {
  return request.post('/report/screen/template', data)
}

export function updateScreenTemplate(data: ScreenTemplateSaveRequest): Promise<void> {
  return request.put('/report/screen/template', data)
}

export function deleteScreenTemplate(id: number): Promise<void> {
  return request.delete(`/report/screen/template/${id}`)
}
