import { describe, expect, it, vi } from 'vitest'
import { navigateToBiz, resolveBizRoute } from '@/utils/bizRoute'

describe('resolveBizRoute', () => {
  it('workflow 带 id 携带详情查询参数', () => {
    expect(resolveBizRoute('workflow', 7)).toEqual({ path: '/system/workflow', query: { detail: 7 } })
  })

  it('workflow 不带 id 无查询参数', () => {
    expect(resolveBizRoute('workflow')).toEqual({ path: '/system/workflow', query: undefined })
  })

  it('file 与 ai 有固定映射', () => {
    expect(resolveBizRoute('file')).toEqual({ path: '/system/file' })
    expect(resolveBizRoute('ai')).toEqual({ path: '/ai/task' })
  })

  it('未知业务类型返回 null', () => {
    expect(resolveBizRoute('unknown')).toBeNull()
    expect(resolveBizRoute(undefined)).toBeNull()
  })
})

describe('navigateToBiz', () => {
  it('有映射时跳转并返回 true', () => {
    const push = vi.fn()
    const router = { push } as never
    expect(navigateToBiz(router, 'workflow', 3)).toBe(true)
    expect(push).toHaveBeenCalledWith({ path: '/system/workflow', query: { detail: 3 } })
  })

  it('无映射时返回 false 且不跳转', () => {
    const push = vi.fn()
    const router = { push } as never
    expect(navigateToBiz(router, 'unknown')).toBe(false)
    expect(push).not.toHaveBeenCalled()
  })
})
