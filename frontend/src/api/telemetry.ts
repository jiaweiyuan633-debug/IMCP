import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface TelemetryPoint {
  key: string
  /** 数字 → value_num，字符串/枚举 → value_text */
  value: unknown
  /** 采集时间，yyyy-MM-dd'T'HH:mm:ss */
  occurredAt: string
}

export interface TelemetryReportRequest {
  /** 客户端生成的幂等上报 ID */
  telemetryId: string
  deviceId: number
  points: TelemetryPoint[]
}

export interface TelemetryLatestVo {
  key: string
  value: unknown
  occurredAt: string
}

export interface TelemetryPointVo {
  id: number
  deviceId: number
  key: string
  valueNum?: number
  valueText?: string
  occurredAt: string
}

export function reportTelemetry(data: TelemetryReportRequest): Promise<void> {
  return request.post('/device/telemetry/report', data)
}

export function getTelemetryLatest(deviceId: number): Promise<TelemetryLatestVo[]> {
  return request.get('/device/telemetry/latest', { params: { deviceId } })
}

export function getTelemetryHistory(params: Record<string, unknown>): Promise<PageResult<TelemetryPointVo>> {
  return request.get('/device/telemetry/history', { params })
}
