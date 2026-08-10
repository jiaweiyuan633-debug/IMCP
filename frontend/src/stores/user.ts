import { defineStore } from 'pinia'
import { changePassword as changePasswordApi, getMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginForm, LoginResponse, UserInfo } from '@/types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'
import { usePermissionStore } from '@/stores/permission'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    userInfo: null as UserInfo | null,
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
      setTokens(data.accessToken, data.refreshToken)
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      this.userInfo = data.user
    },
    async fetchMe(): Promise<UserInfo> {
      const data = await getMe()
      this.userInfo = data
      return data
    },
    async logout() {
      try {
        await logoutApi()
      } finally {
        clearTokens()
        this.accessToken = ''
        this.refreshToken = ''
        this.userInfo = null
        usePermissionStore().reset()
      }
    },
    async changePassword(data: { oldPassword: string; newPassword: string }) {
      await changePasswordApi(data)
    },
    reset() {
      clearTokens()
      this.accessToken = ''
      this.refreshToken = ''
      this.userInfo = null
      usePermissionStore().reset()
    },
  },
})
