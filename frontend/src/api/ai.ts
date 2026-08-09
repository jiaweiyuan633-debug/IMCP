import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface AiConfigVo {
  id: number
  code: string
  name: string
  baseUrl: string
  apiKey?: string
  timeoutSeconds: number
  enabled: number
}

export interface AiTaskResultVo {
  id: number
  taskId: number
  resultType?: string
  resultJson?: string
  rawData?: string
  durationMs?: number
  createdAt?: string
}

export interface AiTaskVo {
  id: number
  taskNo: string
  bizType: string
  bizId?: number
  serviceCode: string
  status: string
  paramsJson?: string
  errorMsg?: string
  retryCount: number
  maxRetry: number
  timeoutSeconds: number
  callbackUrl?: string
  createdBy?: number
  createdAt?: string
  updatedAt?: string
  result?: AiTaskResultVo
}

export function getAiConfigs(): Promise<AiConfigVo[]> {
  return request.get('/ai/config')
}

export function updateAiConfig(
  id: number,
  data: {
    name: string
    baseUrl: string
    apiKey?: string
    timeoutSeconds: number
    enabled: number
  },
): Promise<void> {
  return request.put(`/ai/config/${id}`, { ...data, id })
}

export function createAiTask(data: {
  bizType: string
  serviceCode?: string
  params: Record<string, unknown>
}): Promise<number> {
  return request.post('/ai/tasks', data)
}

export function getAiTaskPage(params: Record<string, unknown>): Promise<PageResult<AiTaskVo>> {
  return request.get('/ai/tasks', { params })
}

export function getAiTaskDetail(id: number): Promise<AiTaskVo> {
  return request.get(`/ai/tasks/${id}`)
}

export function cancelAiTask(id: number): Promise<void> {
  return request.delete(`/ai/tasks/${id}`)
}

export function getAiSseTicket(): Promise<string> {
  return request.get('/ai/ticket')
}

