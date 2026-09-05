// 配置驱动埋点：仅在注入 VITE_GA_ID（GA4 gtag.js）或 VITE_GTM_ID（Google Tag Manager）时
// 才加载第三方脚本并真正上报；未配置（演示/未上线站点）保持 no-op，不伪造上报、不加载脚本。
const GA_ID = (import.meta.env.VITE_GA_ID || '').trim() || undefined
const GTM_ID = (import.meta.env.VITE_GTM_ID || '').trim() || undefined

let initialized = false
let enabled = false

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
  }
}

export function initAnalytics(): void {
  if (initialized) {
    return
  }
  initialized = true
  if (!GA_ID && !GTM_ID) {
    // no-op：仅开发模式提示一次，避免污染生产控制台
    if (import.meta.env.DEV) {
      console.debug('[analytics] VITE_GA_ID / VITE_GTM_ID 均未配置，埋点保持 no-op（演示模式）。')
    }
    return
  }
  enabled = true
  window.dataLayer = window.dataLayer || []
  if (GA_ID) {
    // GA4 gtag.js：dataLayer 以命令数组形式消费 gtag(...) 调用
    const script = document.createElement('script')
    script.async = true
    script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(GA_ID)}`
    document.head.appendChild(script)
    window.gtag = function (...args: unknown[]) {
      window.dataLayer!.push(args)
    }
    window.gtag('js', new Date())
    window.gtag('config', GA_ID)
  } else if (GTM_ID) {
    // GTM 容器：dataLayer 以对象形式消费 trackEvent 的 { event, ... }
    const script = document.createElement('script')
    script.async = true
    script.src = `https://www.googletagmanager.com/gtm.js?id=${encodeURIComponent(GTM_ID)}`
    document.head.appendChild(script)
  }
}

export function trackEvent(name: string, payload: Record<string, unknown> = {}): void {
  if (!enabled) {
    return
  }
  if (window.gtag) {
    window.gtag('event', name, payload)
  } else {
    ;(window.dataLayer ||= []).push({ event: name, ...payload })
  }
}
