import { defineStore } from 'pinia'

export interface TabItem {
  path: string
  title: string
}

export const useAppStore = defineStore('app', {
  state: () => ({
    collapsed: false,
    darkTheme: false,
    tabs: [] as TabItem[],
  }),
  actions: {
    toggleCollapsed() {
      this.collapsed = !this.collapsed
    },
    toggleTheme() {
      this.darkTheme = !this.darkTheme
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

