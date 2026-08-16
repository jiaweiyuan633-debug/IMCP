import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface ThingModelVo {
  id: number
  deviceType: string
  name: string
  description?: string
  propertiesJson?: string
  eventsJson?: string
  servicesJson?: string
  status: number
  /** 乐观锁版本号：编辑时需原样回传 */
  version?: number
  createdAt?: string
}

export interface ThingModelSaveRequest {
  id?: number
  deviceType: string
  name: string
  description?: string
  propertiesJson?: string
  eventsJson?: string
  servicesJson?: string
  status?: number
  version?: number
}

/** 物模型 schema 视图：properties/events/services 反序列化后的列表 */
export interface ThingModelSchemaVo {
  properties: Record<string, unknown>[]
  events: Record<string, unknown>[]
  services: Record<string, unknown>[]
}

export function getThingModelPage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<ThingModelVo>> {
  return request.get('/device/thing-model/page', { params, signal })
}

export function getThingModelDetail(id: number): Promise<ThingModelVo> {
  return request.get(`/device/thing-model/${id}`)
}

export function getThingModelSchema(id: number): Promise<ThingModelSchemaVo> {
  return request.get(`/device/thing-model/${id}/schema`)
}

export function createThingModel(data: ThingModelSaveRequest): Promise<number> {
  return request.post('/device/thing-model', data)
}

export function updateThingModel(data: ThingModelSaveRequest): Promise<void> {
  return request.put('/device/thing-model', data)
}

export function deleteThingModel(id: number): Promise<void> {
  return request.delete(`/device/thing-model/${id}`)
}
