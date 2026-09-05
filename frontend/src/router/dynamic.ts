import type { RouteRecordRaw } from 'vue-router'
import type { MenuNode } from '@/types'
import { resolveMenuPath } from '@/utils/menuPath'

const viewModules = import.meta.glob('../views/**/*.vue')

function loadView(component?: string): () => Promise<unknown> {
  if (!component) {
    return () => import('@/views/not-found/index.vue')
  }
  const candidates = [`../views/${component}.vue`, `../views/${component}/index.vue`]
  const loader = candidates.map((path) => viewModules[path]).find(Boolean)
  if (loader) {
    return loader as () => Promise<unknown>
  }
  return () => import('@/views/not-found/index.vue') as Promise<unknown>
}

export function buildDynamicRouteChildren(menus: MenuNode[], parentPath = '/'): RouteRecordRaw[] {
  return menus
    .filter((menu) => menu.type !== 'button' && menu.status === 1 && menu.visible === 1)
    .map((menu) => {
      const path = menu.path || ''
      const fullPath = resolveMenuPath(parentPath, path)
      const meta = {
        title: menu.name,
        icon: menu.icon,
        perm: menu.perm,
      }
      if (menu.type === 'dir') {
        return {
          path,
          name: `Menu-${menu.id}`,
          meta,
          children: buildDynamicRouteChildren(menu.children || [], fullPath),
        } as RouteRecordRaw
      }
      const routePath = fullPath === parentPath ? '' : path
      return {
        path: routePath,
        name: `Menu-${menu.id}`,
        component: loadView(menu.component),
        meta,
      } as RouteRecordRaw
    })
}
