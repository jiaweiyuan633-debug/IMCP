import type { MenuNode } from '@/types'

/**
 * 菜单路径解析（R4-1.33：去重）。
 *
 * 此前 lastSegment + 路径拼接逻辑在 router/index.ts、router/dynamic.ts、
 * BasicLayout.vue、GlobalSearch.vue 四处各自实现一份，语义完全一致，
 * 一处调整（如对重名子目录的归一）其余三处必然漏改。统一收敛到本模块。
 */

/** 取路径末段（/system/user → user；空路径返回空串）。 */
export function lastSegment(path: string): string {
  const parts = path.split('/').filter(Boolean)
  return parts[parts.length - 1] || ''
}

/**
 * 解析子菜单完整路径，约定与后端菜单下发一致：
 * 1. path 以 / 开头 = 绝对路径，原样返回；
 * 2. path 等于父路径末段 = 自身即父路径（如父 /system/user 下的 path 为 user）；
 * 3. 否则 = 父路径（去尾部 /）+ / + 相对 path。
 */
export function resolveMenuPath(parentPath: string, path: string): string {
  if (path.startsWith('/')) {
    return path
  }
  if (path === lastSegment(parentPath)) {
    return parentPath
  }
  return `${parentPath.replace(/\/$/, '')}/${path}`
}

/** MenuNode 便捷入口：以节点自身 path 解析出完整路径，parentPath 默认根路径。 */
export function fullPathOf(menu: MenuNode, parentPath = '/'): string {
  return resolveMenuPath(parentPath, menu.path || '')
}
