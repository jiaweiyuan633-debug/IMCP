import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePermissionStore } from '@/stores/permission'
import type { UserInfo } from '@/types'

/**
 * 权限 store 契约测试——setRoutes 应用菜单/权限/路由加载标记，
 * reset 全量清空。此前 router/stores 为前端安全相关逻辑但零测试（覆盖率门槛被
 * utils 100% 拉高掩盖）。
 */
describe('permission store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('setRoutes 应用菜单/权限并标记路由已加载', () => {
    const store = usePermissionStore()
    const user: UserInfo = {
      id: 1,
      username: 'admin',
      roles: ['admin'],
      perms: ['system:user:list', 'system:user:add'],
      menus: [
        {
          id: 1,
          parentId: 0,
          name: '系统管理',
          type: 'dir',
          path: '/system',
          sort: 1,
          visible: 1,
          status: 1,
          children: [
            { id: 2, parentId: 1, name: '用户管理', type: 'menu', path: 'user', component: 'system/user', perm: 'system:user:list', sort: 1, visible: 1, status: 1 },
          ],
        },
      ],
    }

    store.setRoutes(user)

    expect(store.routesLoaded).toBe(true)
    expect(store.menus).toHaveLength(1)
    expect(store.menus[0].children).toHaveLength(1)
    expect(store.perms).toContain('system:user:add')
  })

  it('setRoutes 容忍缺失菜单/权限（空数组而非 undefined）', () => {
    const store = usePermissionStore()
    store.setRoutes({ id: 1, username: 'u', roles: [], perms: [], menus: [] })

    expect(store.menus).toEqual([])
    expect(store.perms).toEqual([])
    expect(store.routesLoaded).toBe(true)
  })

  it('reset 清空全部状态', () => {
    const store = usePermissionStore()
    store.setRoutes({
      id: 1,
      username: 'u',
      roles: ['admin'],
      perms: ['system:user:list'],
      menus: [{ id: 1, parentId: 0, name: 'M', type: 'menu', path: 'm', sort: 1, visible: 1, status: 1 }],
    })

    store.reset()

    expect(store.menus).toEqual([])
    expect(store.perms).toEqual([])
    expect(store.routesLoaded).toBe(false)
  })
})
