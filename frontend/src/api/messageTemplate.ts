import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface MessageTemplateVo {
  id: number
  templateCode: string
  templateName: string
  messageType?: string
  titleTemplate: string
  contentTemplate: string
  contentType?: string
  status: number
  remark?: string
  createdAt?: string
}

export interface MessageTemplateSaveRequest {
  id?: number
  templateCode: string
  templateName: string
  messageType?: string
  titleTemplate: string
  contentTemplate: string
  contentType?: string
  status?: number
  remark?: string
}

export interface MessageTemplateSendRequest {
  templateCode: string
  /** 模板占位符参数，渲染后填充 ${key} */
  params?: Record<string, unknown>
  bizType?: string
  bizId?: number
  /** 为空表示广播（全体用户）；否则发送给指定用户 */
  receiverIds?: number[]
}

export function getMessageTemplatePage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<MessageTemplateVo>> {
  return request.get('/notice/message-template', { params, signal })
}

export function createMessageTemplate(data: MessageTemplateSaveRequest): Promise<number> {
  return request.post('/notice/message-template', data)
}

export function updateMessageTemplate(data: MessageTemplateSaveRequest): Promise<void> {
  return request.put('/notice/message-template', data)
}

export function updateMessageTemplateStatus(id: number, status: number): Promise<void> {
  return request.put(`/notice/message-template/${id}/status`, { status })
}

export function deleteMessageTemplate(id: number): Promise<void> {
  return request.delete(`/notice/message-template/${id}`)
}

/** 按模板发送消息，返回消息 id */
export function sendMessageTemplate(data: MessageTemplateSendRequest): Promise<number> {
  return request.post('/notice/message-template/send', data)
}
