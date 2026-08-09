import { onMounted, reactive, ref } from 'vue'
import type { PageResult } from '@/types'

/**
 * 列表页统一状态管理：分页 / 加载 / 查询 / 错误。
 *
 * 用法：
 * ```ts
 * const { pageNum, pageSize, total, loading, records, searchModel,
 *         error, loadData, onSearch, onReset } = useTableQuery(getOperLogPage, {
 *   buildParams: (query) => ({ module: (query.module as string) || undefined }),
 * })
 * ```
 * 默认在 onMounted 自动加载；多列表页可传 `immediate: false` 自行控制。
 */
export type TableQueryFetcher<T, P extends Record<string, unknown>> = (
  params: P & { pageNum: number; pageSize: number },
) => Promise<PageResult<T>>

export interface UseTableQueryOptions<P extends Record<string, unknown>> {
  pageNum?: number
  pageSize?: number
  /** 默认 true，在 onMounted 时自动加载 */
  immediate?: boolean
  /** 把搜索模型映射为接口查询参数，默认原样透传 */
  buildParams?: (query: Record<string, unknown>) => Partial<P>
}

export function useTableQuery<T, P extends Record<string, unknown> = Record<string, unknown>>(
  fetcher: TableQueryFetcher<T, P>,
  options: UseTableQueryOptions<P> = {},
) {
  const pageNum = ref(options.pageNum ?? 1)
  const pageSize = ref(options.pageSize ?? 10)
  const total = ref(0)
  const loading = ref(false)
  const records = ref<T[]>([])
  const searchModel = reactive<Record<string, unknown>>({})
  const error = ref<Error | null>(null)

  const buildParams = options.buildParams ?? ((query: Record<string, unknown>) => ({ ...query }) as Partial<P>)

  async function loadData() {
    loading.value = true
    error.value = null
    try {
      const params = {
        ...buildParams(searchModel),
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      } as P & { pageNum: number; pageSize: number }
      const data = await fetcher(params)
      records.value = data.records
      total.value = data.total
    } catch (err) {
      error.value = err instanceof Error ? err : new Error(String(err))
      records.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function onSearch(model: Record<string, unknown>) {
    Object.assign(searchModel, model)
    pageNum.value = 1
    loadData()
  }

  function onReset() {
    Object.keys(searchModel).forEach((key) => {
      searchModel[key] = undefined
    })
    pageNum.value = 1
    loadData()
  }

  if (options.immediate !== false) {
    onMounted(loadData)
  }

  return { pageNum, pageSize, total, loading, records, searchModel, error, loadData, onSearch, onReset }
}
