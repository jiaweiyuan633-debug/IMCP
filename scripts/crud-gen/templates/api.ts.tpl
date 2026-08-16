import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface {{Entity}}Vo {
  id: number
[[for:ts_vo_fields]]{{item}}
[[/for]]  createdBy?: number
  createdAt?: string
  updatedAt?: string
}

export interface {{Entity}}SaveRequest {
  id?: number
[[for:ts_req_fields]]{{item}}
[[/for]]}

export function get{{Entity}}Page(params: Record<string, unknown>): Promise<PageResult<{{Entity}}Vo>> {
  return request.get('/{{module}}/{{kebab}}', { params })
}

export function create{{Entity}}(data: {{Entity}}SaveRequest): Promise<number> {
  return request.post('/{{module}}/{{kebab}}', data)
}

export function update{{Entity}}(data: {{Entity}}SaveRequest): Promise<void> {
  return request.put('/{{module}}/{{kebab}}', data)
}

export function delete{{Entity}}(id: number): Promise<void> {
  return request.delete(`/{{module}}/{{kebab}}/${id}`)
}
