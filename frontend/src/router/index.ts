import { createRouter, createWebHistory } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { buildDynamicRouteChildren } from '@/router/dynamic'
import BasicLayout from '@/layout/BasicLayout.vue'
import type { MenuNode } from '@/types'
import { useAppStore } from '@/stores/app'
import { fullPathOf } from '@/utils/menuPath'

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
      // R4-1.47（批次1）：强制改密页——登录后若 mustChangePassword 则只能访问此页
      path: '/change-password',
      name: 'ChangePassword',
      component: () => import('@/views/change-password/index.vue'),
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

function firstMenuPath(menus: MenuNode[], parentPath = '/'): string {
  for (const menu of menus) {
    const fullPath = fullPathOf(menu, parentPath)
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

  // R4-1.47（批次1）：强制改密拦截——必须改密的用户只能访问改密页/登录页，
  // 其余页面一律重定向到 /change-password（改密成功后 userStore.mustChangePassword 置 false）
  if (userStore.mustChangePassword && to.path !== '/change-password') {
    return { path: '/change-password' }
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
