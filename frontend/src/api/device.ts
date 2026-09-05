import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface DeviceVo {
  id: number
  deviceCode: string
  deviceName: string
  deviceType?: string
  location?: string
  sort: number
  status: number
  description?: string
  createdAt?: string
}

export interface DeviceSaveRequest {
  id?: number
  deviceCode: string
  deviceName: string
  deviceType?: string
  location?: string
  sort?: number
  status?: number
  description?: string
}

export function getDevicePage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<DeviceVo>> {
  return request.get('/device', { params, signal })
}

export function createDevice(data: DeviceSaveRequest): Promise<number> {
  return request.post('/device', data)
}

export function updateDevice(data: DeviceSaveRequest): Promise<void> {
  return request.put('/device', data)
}

export function updateDeviceStatus(id: number, status: number): Promise<void> {
  return request.put(`/device/${id}/status`, { status })
}

export function deleteDevice(id: number): Promise<void> {
  return request.delete(`/device/${id}`)
}
