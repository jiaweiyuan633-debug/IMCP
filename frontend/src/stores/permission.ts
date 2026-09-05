import { defineStore } from 'pinia'
import type { MenuNode, UserInfo } from '@/types'

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    menus: [] as MenuNode[],
    perms: [] as string[],
    routesLoaded: false,
  }),
  actions: {
    setRoutes(user: UserInfo) {
      this.menus = user.menus || []
      this.perms = user.perms || []
      this.routesLoaded = true
    },
    reset() {
      this.menus = []
      this.perms = []
      this.routesLoaded = false
    },
  },
})

