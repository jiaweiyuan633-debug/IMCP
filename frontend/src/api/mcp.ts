import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface McpServerVo {
  id: number
  name: string
  url: string
  hasAuthToken?: boolean
  enabled: number
  sort: number
  remark?: string
  createdAt?: string
}

export interface McpServerSaveRequest {
  id?: number
  name: string
  url: string
  authToken?: string
  enabled?: number
  sort?: number
  remark?: string
}

export interface McpToolVo {
  name: string
  title?: string
  description?: string
}

export interface McpCallResultVo {
  isError: boolean
  content: string[]
  structuredContent?: Record<string, unknown>
}

export function getMcpServerPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<McpServerVo>> {
  return request.get('/mcp/server', { params, signal })
}

export function createMcpServer(data: McpServerSaveRequest): Promise<number> {
  return request.post('/mcp/server', data)
}

export function updateMcpServer(data: McpServerSaveRequest): Promise<void> {
  return request.put('/mcp/server', data)
}

export function updateMcpServerStatus(id: number, enabled: number): Promise<void> {
  return request.put(`/mcp/server/${id}/status`, { enabled })
}

export function deleteMcpServer(id: number): Promise<void> {
  return request.delete(`/mcp/server/${id}`)
}

export function getMcpServerTools(id: number): Promise<McpToolVo[]> {
  return request.get(`/mcp/server/${id}/tools`)
}

export function callMcpTool(id: number, data: { toolName: string; arguments?: Record<string, unknown> }): Promise<McpCallResultVo> {
  return request.post(`/mcp/server/${id}/call`, data)
}
