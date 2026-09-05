import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import { mountWithPlugins } from '@/test/testUtils'
import { useUserStore } from '@/stores/user'
import { useSystemTitle } from '@/composables/useSystemTitle'
import type { UserInfo } from '@/types'

const adminUser: UserInfo = {
  id: 1,
  username: 'admin',
  nickname: '管理员',
  roles: ['admin'],
  perms: ['*'],
  menus: [],
}

function mountTitle(seed?: () => void) {
  const Harness = defineComponent({
    setup() {
      if (seed) seed()
      const title = useSystemTitle()
      return { title }
    },
    template: '<div class="title">{{ title }}</div>',
  })
  return mountWithPlugins(Harness)
}

describe('useSystemTitle', () => {
  it('未登录显示平台名', () => {
    const wrapper = mountTitle()
    expect(wrapper.find('.title').text()).toBe('智能管理平台')
  })

  it('管理员登录显示后台管理系统', () => {
    const wrapper = mountTitle(() => {
      const store = useUserStore()
      store.userInfo = adminUser
      store.accessToken = 'token-1'
    })
    expect(wrapper.find('.title').text()).toBe('后台管理系统')
  })

  it('普通用户登录显示用户个人管理系统', () => {
    const wrapper = mountTitle(() => {
      const store = useUserStore()
      store.userInfo = { ...adminUser, id: 2, username: 'bob', roles: ['user'] }
      store.accessToken = 'token-2'
    })
    expect(wrapper.find('.title').text()).toBe('用户个人管理系统')
  })
})
