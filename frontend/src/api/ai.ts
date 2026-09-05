import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface AiConfigVo {
  id: number
  code: string
  provider?: string
  name: string
  model?: string
  baseUrl: string
  hasApiKey?: boolean
  timeoutSeconds: number
  enabled: number
  dailyLimit?: number
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
  /** 服务展示名（列表批量解析，缺省回退 serviceCode） */
  serviceName?: string
  status: string
  paramsJson?: string
  errorMsg?: string
  errorType?: string
  retryCount: number
  maxRetry: number
  timeoutSeconds: number
  callbackUrl?: string
  createdBy?: number
  /** 创建人姓名（列表批量解析，缺省留空由前端兜底 '-'） */
  createdByName?: string
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
    provider?: string
    model?: string
    baseUrl: string
    apiKey?: string
    timeoutSeconds: number
    enabled: number
    dailyLimit?: number
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

export function getAiTaskPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<AiTaskVo>> {
  return request.get('/ai/tasks', { params, signal })
}

export function getAiTaskDetail(id: number): Promise<AiTaskVo> {
  return request.get(`/ai/tasks/${id}`)
}

export function cancelAiTask(id: number): Promise<void> {
  return request.delete(`/ai/tasks/${id}`)
}

export interface AiTaskRetryResult {
  total: number
  succeeded: number
  skipped: number
  failed: number
  failedIds: number[]
}

/** 批量重试终态失败任务（后端单次上限 100），返回成功/跳过/失败分类计数。 */
export function retryAiTasks(ids: number[]): Promise<AiTaskRetryResult> {
  return request.post('/ai/tasks/retry', { ids })
}

export function getAiSseTicket(): Promise<string> {
  return request.get('/ai/ticket')
}

