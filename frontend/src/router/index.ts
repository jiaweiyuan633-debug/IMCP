import { createRouter, createWebHistory } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { buildDynamicRouteChildren } from '@/router/dynamic'
import BasicLayout from '@/layout/BasicLayout.vue'
import type { MenuNode } from '@/types'
import { useAppStore } from '@/stores/app'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/oauth/callback',
      name: 'OauthCallback',
      component: () => import('@/views/oauth/callback.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'Root',
      component: BasicLayout,
      children: [],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/not-found/index.vue'),
    },
  ],
})

let recoveringNotFound = false

function lastSegment(path: string): string {
  const parts = path.split('/').filter(Boolean)
  return parts[parts.length - 1] || ''
}

function resolveMenuPath(menu: MenuNode, parentPath = '/'): string {
  const path = menu.path || ''
  if (path.startsWith('/')) {
    return path
  }
  if (path === lastSegment(parentPath)) {
    return parentPath
  }
  return `${parentPath.replace(/\/$/, '')}/${path}`
}

function firstMenuPath(menus: MenuNode[], parentPath = '/'): string {
  for (const menu of menus) {
    const fullPath = resolveMenuPath(menu, parentPath)
    if (menu.type === 'dir') {
      const childPath = firstMenuPath(menu.children || [], fullPath)
      if (childPath) {
        return childPath
      }
    }
    if (menu.type === 'menu' && menu.status === 1 && menu.visible === 1) {
      return fullPath
    }
  }
  return '/profile'
}

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  if (to.name === 'NotFound' && userStore.isLoggedIn && !recoveringNotFound) {
    recoveringNotFound = true
    try {
      removeDynamicRoutes()
      permissionStore.reset()
      const user = userStore.userInfo || (await userStore.fetchMe())
      const routes = buildDynamicRouteChildren(user.menus)
      routes.forEach((route) => {
        if (route.name && !router.hasRoute(route.name)) {
          router.addRoute('Root', route)
        }
      })
      permissionStore.setRoutes(user)
      return { path: to.fullPath, replace: true }
    } finally {
      recoveringNotFound = false
    }
  }

  if (to.path === '/login') {
    removeDynamicRoutes()
    if (!userStore.isLoggedIn) {
      useAppStore().resetTabs()
    }
    return userStore.isLoggedIn ? { path: firstMenuPath(permissionStore.menus) } : true
  }
  // 第三方登录回跳页：未登录也可访问
  if (to.meta.public) {
    return true
  }
  if (!userStore.isLoggedIn) {
    removeDynamicRoutes()
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!permissionStore.routesLoaded) {
    try {
      const user = userStore.userInfo || (await userStore.fetchMe())
      removeDynamicRoutes()
      const routes = buildDynamicRouteChildren(user.menus)
      routes.forEach((route) => {
        if (route.name && !router.hasRoute(route.name)) {
          router.addRoute('Root', route)
        }
      })
      permissionStore.setRoutes(user)
      return { ...to, replace: true }
    } catch {
      userStore.reset()
      return { path: '/login' }
    }
  }
  if (to.path === '/') {
    return { path: firstMenuPath(permissionStore.menus), replace: true }
  }
  return true
})

function removeDynamicRoutes() {
  router.getRoutes()
    .filter((route) => route.name && String(route.name).startsWith('Menu-'))
    .forEach((route) => {
      if (route.name) {
        router.removeRoute(route.name)
      }
    })
}

export default router
