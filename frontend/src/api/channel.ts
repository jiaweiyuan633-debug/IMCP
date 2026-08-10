import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface ChannelConfigVo {
  id: number
  channelType: string
  channelName: string
  configJson?: string
  status: number
  sort: number
  description?: string
  createdAt?: string
}

export interface ChannelConfigSaveRequest {
  id?: number
  channelType: string
  channelName: string
  configJson: string
  status?: number
  sort?: number
  description?: string
}

export interface ChannelSendRequest {
  channelId: number
  target: string
  title: string
  content?: string
}

export interface ChannelLogVo {
  id: number
  channelType: string
  channelId: number
  target?: string
  title?: string
  content?: string
  status: number
  errorMsg?: string
  createdAt?: string
}

export function getChannelPage(params: Record<string, unknown>): Promise<PageResult<ChannelConfigVo>> {
  return request.get('/notice/channel', { params })
}

export function createChannel(data: ChannelConfigSaveRequest): Promise<number> {
  return request.post('/notice/channel', data)
}

export function updateChannel(data: ChannelConfigSaveRequest): Promise<void> {
  return request.put('/notice/channel', data)
}

export function updateChannelStatus(id: number, status: number): Promise<void> {
  return request.put(`/notice/channel/${id}/status`, { status })
}

export function deleteChannel(id: number): Promise<void> {
  return request.delete(`/notice/channel/${id}`)
}

export function sendChannelMessage(data: ChannelSendRequest): Promise<number> {
  return request.post('/notice/channel/send', data)
}

export function getChannelLogPage(params: Record<string, unknown>): Promise<PageResult<ChannelLogVo>> {
  return request.get('/notice/channel/log', { params })
}
