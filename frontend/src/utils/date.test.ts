import { describe, expect, it } from 'vitest'
import { formatDateTime } from './date'

describe('formatDateTime', () => {
  it('formats ISO datetime to YYYY-MM-DD HH:mm', () => {
    // 后端 LocalDateTime 以 ISO 串（无时区）透出，按本地墙钟重排为易读格式
    expect(formatDateTime('2026-08-14T10:30:00')).toBe('2026-08-14 10:30')
  })

  it('returns fallback for null/undefined/empty', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
  })

  it('returns custom fallback when provided', () => {
    expect(formatDateTime(undefined, '--')).toBe('--')
  })

  it('returns fallback for invalid values', () => {
    // dayjs 对日期回绕（如 2026-13-99）是宽松解析并回绕成合法日期，故只用真正非法串断言
    expect(formatDateTime('not-a-date')).toBe('-')
  })
})
