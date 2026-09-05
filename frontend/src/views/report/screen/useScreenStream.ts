import { onBeforeUnmount, ref } from 'vue'
import type { ReportScreenVo } from '@/api/report'
import { getReportScreen, getReportScreenTicket } from '@/api/report'
import { API_BASE_URL } from '@/utils/env'

/**
 * 数据大屏实时数据：先拉一次快照保证首屏即渲染，再订阅 SSE（每 30s 由后端广播最新聚合）。
 * SSE 断开或票据获取失败时自动回退 60s 轮询，页面始终有数据可刷。
 */
export function useScreenStream() {
  const stats = ref<ReportScreenVo>()
  const connected = ref(false)
  let source: EventSource | null = null
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let disposed = false

  async function loadOnce() {
    try {
      stats.value = await getReportScreen()
    } catch {
      // 首屏取数失败不影响后续（SSE/轮询会补）
    }
  }

  function startPolling() {
    if (pollTimer) {
      return
    }
    pollTimer = setInterval(async () => {
      try {
        stats.value = await getReportScreen()
      } catch {
        // 网络抖动忽略，下一轮继续
      }
    }, 60_000)
  }

  async function connect() {
    await loadOnce()
    if (disposed) {
      return
    }
    try {
      const ticket = await getReportScreenTicket()
      source = new EventSource(`${API_BASE_URL}/report/screen/stream?ticket=${encodeURIComponent(ticket)}`)
      source.addEventListener('screen', (event) => {
        try {
          stats.value = JSON.parse((event as MessageEvent<string>).data) as ReportScreenVo
          connected.value = true
        } catch {
          // 忽略非法载荷，等待下一帧
        }
      })
      source.onerror = () => {
        source?.close()
        source = null
        connected.value = false
        startPolling()
      }
    } catch {
      startPolling()
    }
  }

  onBeforeUnmount(() => {
    disposed = true
    source?.close()
    source = null
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  })

  return { stats, connected, connect }
}
