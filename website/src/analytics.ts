export function trackEvent(name: string, payload: Record<string, unknown> = {}) {
  const dataLayer = (window as unknown as { dataLayer?: Array<Record<string, unknown>> }).dataLayer || []
  dataLayer.push({ event: name, ...payload })
  ;(window as unknown as { dataLayer?: Array<Record<string, unknown>> }).dataLayer = dataLayer
}
