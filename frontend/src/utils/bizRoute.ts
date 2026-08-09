import type { Router } from 'vue-router'

/**
 * 业务类型 → 业务页面路由映射。
 * 消息/铃铛深链按 bizType 跳到对应业务页面；无映射返回 null。
 */
export function resolveBizRoute(
  bizType?: string,
  bizId?: number,
): { path: string; query?: Record<string, string | number> } | null {
  switch (bizType) {
    case 'workflow':
      return { path: '/system/workflow', query: bizId ? { detail: bizId } : undefined }
    case 'file':
      return { path: '/system/file' }
    case 'ai':
      return { path: '/ai/task' }
    default:
      return null
  }
}

/**
 * 按 bizType 跳转业务页面；有映射则跳转并返回 true，否则返回 false。
 */
export function navigateToBiz(router: Router, bizType?: string, bizId?: number): boolean {
  const target = resolveBizRoute(bizType, bizId)
  if (!target) {
    return false
  }
  router.push(target)
  return true
}
