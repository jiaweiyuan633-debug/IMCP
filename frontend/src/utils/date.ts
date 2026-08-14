import dayjs from 'dayjs'

/** 统一日期时间展示格式：YYYY-MM-DD HH:mm（本地时区）。 */
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm'

/**
 * 统一日期时间格式化：后端 LocalDateTime 以 ISO 串（如 2026-08-14T10:30:00）透出，
 * 直接展示带 T 的原始串不友好；此处统一渲染为 YYYY-MM-DD HH:mm。
 * 空值 / 非法值返回 fallback（默认 '-'），避免 null / "Invalid Date" 直接上屏。
 */
export function formatDateTime(value?: string | number | Date | null, fallback = '-'): string {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format(DATETIME_FORMAT) : fallback
}
