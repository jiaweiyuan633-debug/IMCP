import { createRouter, createWebHistory } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { buildDynamicRouteChildren } from '@/router/dynamic'
import BasicLayout from '@/layout/BasicLayout.vue'

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
      path: '/',
      name: 'Root',
      component: BasicLayout,
      redirect: '/dashboard',
      children: [],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/not-found/index.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  if (to.path === '/login') {
    removeDynamicRoutes()
    return userStore.isLoggedIn ? { path: '/' } : true
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
