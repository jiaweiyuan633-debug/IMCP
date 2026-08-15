import { defineStore } from 'pinia'

export interface TabItem {
  path: string
  title: string
}

export const useAppStore = defineStore('app', {
  state: () => ({
    collapsed: false,
    // R4-1.33：暗黑主题持久化——初始化即从 localStorage 恢复用户偏好，切换即写回，
    // 刷新/重开浏览器不丢主题选择；App.vue 的 themeConfig 已响应 darkTheme 实时应用
    darkTheme: localStorage.getItem('admin_dark_theme') === '1',
    locale: localStorage.getItem('admin_locale') || 'zh-CN',
    tabs: [] as TabItem[],
  }),
  actions: {
    toggleCollapsed() {
      this.collapsed = !this.collapsed
    },
    toggleTheme() {
      this.darkTheme = !this.darkTheme
      localStorage.setItem('admin_dark_theme', this.darkTheme ? '1' : '0')
    },
    setDarkTheme(dark: boolean) {
      this.darkTheme = dark
      localStorage.setItem('admin_dark_theme', dark ? '1' : '0')
    },
    setLocale(locale: string) {
      this.locale = locale
      localStorage.setItem('admin_locale', locale)
    },
    addTab(tab: TabItem) {
      if (this.tabs.some((item) => item.path === tab.path)) {
        return
      }
      this.tabs.push(tab)
    },
    removeTab(path: string) {
      this.tabs = this.tabs.filter((item) => item.path !== path)
    },
    resetTabs() {
      this.tabs = []
    },
  },
})

