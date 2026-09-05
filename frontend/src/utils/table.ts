import { formatDateTime } from '@/utils/date'

export { formatDateTime } from '@/utils/date'

/**
 * 列表日期列列定义：统一按本地时区渲染为 YYYY-MM-DD HH:mm，空值显示 '-'。
 * 与 antd-vue customRender 语义对齐：columns 里任何 time 列都用它，避免 raw ISO 串上屏。
 *
 * 用法：dateColumn('createdAt', { title: t('page.xxx'), width: 170 })
 */
export function dateColumn(
  dataIndex: string,
  opts: { title?: string; width?: number } = {},
): Record<string, unknown> {
  return {
    ...opts,
    dataIndex,
    key: dataIndex,
    customRender: ({ text }: { text?: unknown }) => formatDateTime(text as string | undefined),
  }
}
