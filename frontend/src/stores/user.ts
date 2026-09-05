import { defineStore } from 'pinia'
import { changePassword as changePasswordApi, getMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginForm, LoginResponse, UserInfo } from '@/types'
import { clearTokens, getAccessToken, setTokens } from '@/utils/auth'
import { usePermissionStore } from '@/stores/permission'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken(),
    userInfo: null as UserInfo | null,
    // 记录"必须修改密码"状态（默认口令首登 / 密码过期），由路由守卫强制跳转改密页
    mustChangePassword: false,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
  },
  actions: {
    async login(form: LoginForm): Promise<LoginResponse> {
      const data = await loginApi(form)
      this.applyLogin(data)
      return data
    },
    /** 应用一次完整登录结果（密码登录 / 第三方登录 / 绑定后登录共用）。 */
    applyLogin(data: LoginResponse) {
      // refresh token 已迁移 httpOnly Cookie，前端仅持久化 access token
      setTokens(data.accessToken)
      this.accessToken = data.accessToken
      this.userInfo = data.user
      this.mustChangePassword = Boolean(data.mustChangePassword || data.user?.mustChangePassword)
    },
    async fetchMe(): Promise<UserInfo> {
      const data = await getMe()
      this.userInfo = data
      this.mustChangePassword = Boolean(data.mustChangePassword)
      return data
    },
    async logout() {
      try {
        await logoutApi()
      } finally {
        clearTokens()
        this.accessToken = ''
        this.userInfo = null
        this.mustChangePassword = false
        usePermissionStore().reset()
      }
    },
    async changePassword(data: { oldPassword: string; newPassword: string }) {
      await changePasswordApi(data)
      // 改密成功后清除强制改密标记（后端已清 must_change_password 并记录改密时间）
      this.mustChangePassword = false
    },
    reset() {
      clearTokens()
      this.accessToken = ''
      this.userInfo = null
      this.mustChangePassword = false
      usePermissionStore().reset()
    },
  },
})
