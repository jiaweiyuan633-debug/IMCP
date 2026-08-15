import { onMounted, onUnmounted, reactive, ref } from 'vue'
import type { PageResult } from '@/types'

/**
 * 判断错误是否来自主动取消（AbortController）：
 * 原生 fetch abort 为 DOMException(AbortError)；axios 经 signal 取消为 CanceledError(code=ERR_CANCELED)。
 */
function isAbortError(err: unknown): boolean {
  if (!(err instanceof Error)) {
    return false
  }
  const code = (err as Error & { code?: string }).code
  return err.name === 'AbortError' || code === 'ERR_CANCELED'
}

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
  signal?: AbortSignal,
) => Promise<PageResult<T>>
/**
 * signal 为可选：仅显式支持 signal 的 fetcher（api 函数接收第二个参数并透传给 request）
 * 能真正中断网络请求；现有 `(params) => Promise` 形式的 api 函数自动忽略该参数，
 * 由 requestSeq 守卫兜底防止过期响应写回。
 */

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

  // 请求序号守卫：快速翻页/搜索时可能同时存在多个在途请求，
  // 仅最新请求允许写回状态；迟到的旧响应直接丢弃，否则慢请求后返回会
  // 覆盖用户当前看到的更新数据（records/total 错乱、loading 提前结束）。
  let requestSeq = 0
  // R4-1.33：在途请求取消——新请求 abort 上一个、组件卸载 abort 全部，
  // 配合 request.ts 对 ERR_CANCELED 的静默透传，让支持 signal 的 fetcher 真正中断网络
  let controller: AbortController | null = null

  async function loadData() {
    const seq = ++requestSeq
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    error.value = null
    try {
      const params = {
        ...buildParams(searchModel),
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      } as P & { pageNum: number; pageSize: number }
      const data = await fetcher(params, signal)
      if (seq !== requestSeq) {
        return
      }
      records.value = data.records
      total.value = data.total
    } catch (err) {
      // 主动取消不是业务错误：静默返回，不污染 error/records
      if (isAbortError(err)) {
        return
      }
      if (seq !== requestSeq) {
        return
      }
      error.value = err instanceof Error ? err : new Error(String(err))
      records.value = []
      total.value = 0
    } finally {
      // 过期请求不得关闭 loading：新的在途请求仍需 loading 状态
      if (seq === requestSeq) {
        loading.value = false
      }
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

  // 组件卸载时中断仍在途的请求，避免「页面已离开、请求还在飞」的资源浪费
  onUnmounted(() => {
    controller?.abort()
    controller = null
  })

  return { pageNum, pageSize, total, loading, records, searchModel, error, loadData, onSearch, onReset }
}
