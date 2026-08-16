import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface PromptVo {
  id: number
  code: string
  name: string
  content: string
  variables?: string
  status: number
  sort: number
  description?: string
  createdAt?: string
}

export interface PromptSaveRequest {
  id?: number
  code: string
  name: string
  content: string
  variables?: string
  status?: number
  sort?: number
  description?: string
}

export interface KnowledgeBaseVo {
  id: number
  name: string
  description?: string
  status: number
  createdAt?: string
}

export interface KnowledgeBaseSaveRequest {
  id?: number
  name: string
  description?: string
  status?: number
}

export interface KnowledgeDocVo {
  id: number
  baseId: number
  title: string
  content?: string
  chunkIndex: number
  status: number
  createdAt?: string
}

export interface KnowledgeDocSaveRequest {
  id?: number
  baseId: number
  title: string
  content?: string
  status?: number
}

export interface AiChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface AiChatRequest {
  serviceCode: string
  model?: string
  templateCode?: string
  templateParams?: Record<string, unknown>
  useKnowledge?: boolean
  knowledgeBaseId?: number
  topK?: number
  temperature?: number
  messages: AiChatMessage[]
}

export interface AiChatVo {
  content: string
  model?: string
  provider?: string
  durationMs: number
  status: number
}

// ---------- 对话 ----------

export function chatAi(data: AiChatRequest): Promise<AiChatVo> {
  return request.post('/ai/chat', data)
}

// ---------- Prompt 模板 ----------

export function getPromptPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<PromptVo>> {
  return request.get('/ai/prompt', { params, signal })
}

export function createPrompt(data: PromptSaveRequest): Promise<number> {
  return request.post('/ai/prompt', data)
}

export function updatePrompt(data: PromptSaveRequest): Promise<void> {
  return request.put(`/ai/prompt/${data.id}`, data)
}

export function deletePrompt(id: number): Promise<void> {
  return request.delete(`/ai/prompt/${id}`)
}

// ---------- 知识库 ----------

export function getKnowledgePage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<KnowledgeBaseVo>> {
  return request.get('/ai/knowledge', { params, signal })
}

export function createKnowledge(data: KnowledgeBaseSaveRequest): Promise<number> {
  return request.post('/ai/knowledge', data)
}

export function updateKnowledge(data: KnowledgeBaseSaveRequest): Promise<void> {
  return request.put(`/ai/knowledge/${data.id}`, data)
}

export function deleteKnowledge(id: number): Promise<void> {
  return request.delete(`/ai/knowledge/${id}`)
}

// ---------- 知识库文档 ----------

export function getKnowledgeDocPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<KnowledgeDocVo>> {
  return request.get('/ai/knowledge-doc', { params, signal })
}

export function createKnowledgeDoc(data: KnowledgeDocSaveRequest): Promise<number> {
  return request.post('/ai/knowledge-doc', data)
}

export function updateKnowledgeDoc(data: KnowledgeDocSaveRequest): Promise<void> {
  return request.put(`/ai/knowledge-doc/${data.id}`, data)
}

export function deleteKnowledgeDoc(id: number): Promise<void> {
  return request.delete(`/ai/knowledge-doc/${id}`)
}

export function getKnowledgeOptions(): Promise<KnowledgeBaseVo[]> {
  return request.get('/ai/knowledge/options')
}
