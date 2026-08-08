<template>
  <a-layout class="app-layout" style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="appStore.collapsed" :width="220" theme="dark" collapsible :trigger="null">
      <div class="app-logo">
        <ApiOutlined v-if="appStore.collapsed" />
        <template v-else>双端管理脚手架</template>
      </div>
      <a-menu :selected-keys="[route.path]" theme="dark" mode="inline">
        <template v-for="menu in permissionStore.menus" :key="fullPath(menu)">
          <a-sub-menu v-if="hasChildren(menu)" :key="fullPath(menu)">
            <template #title>
              <component :is="iconOf(menu.icon)" />
              <span>{{ menu.name }}</span>
            </template>
            <a-menu-item
              v-for="child in menu.children"
              :key="fullPath(child, fullPath(menu))"
              @click="navigate(fullPath(child, fullPath(menu)))"
            >
              <component :is="iconOf(child.icon)" />
              <span>{{ child.name }}</span>
            </a-menu-item>
          </a-sub-menu>
          <a-menu-item v-else :key="fullPath(menu)" @click="navigate(fullPath(menu))">
            <component :is="iconOf(menu.icon)" />
            <span>{{ menu.name }}</span>
          </a-menu-item>
        </template>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="app-header">
        <div class="header-left">
          <a-button type="text" @click="appStore.toggleCollapsed()">
            <MenuUnfoldOutlined v-if="appStore.collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <a-breadcrumb>
            <a-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="header-right">
          <a-button type="text" @click="appStore.toggleTheme()">
            <BulbOutlined v-if="!appStore.darkTheme" />
            <BulbFilled v-else />
          </a-button>
          <a-dropdown>
            <span class="user-entry">
              <UserOutlined />
              {{ userStore.userInfo?.nickname || userStore.userInfo?.username }}
            </span>
            <template #overlay>
              <a-menu @click="onUserMenuClick">
                <a-menu-item key="profile">个人中心</a-menu-item>
                <a-menu-item key="logout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <a-layout-content class="app-content">
        <a-tabs
          class="app-tabs"
          type="editable-card"
          :active-key="route.path"
          @change="navigate"
          @edit="onTabEdit"
        >
          <a-tab-pane
            v-for="tab in appStore.tabs"
            :key="tab.path"
            :tab="tab.title"
            :closable="tab.path !== '/dashboard'"
          />
        </a-tabs>
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import {
  ApiOutlined,
  BarChartOutlined,
  BulbFilled,
  BulbOutlined,
  CarryOutOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HistoryOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MonitorOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  WifiOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import type { MenuNode } from '@/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const permissionStore = usePermissionStore()
const userStore = useUserStore()

const iconMap: Record<string, Component> = {
  ApiOutlined,
  BarChartOutlined,
  CarryOutOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HistoryOutlined,
  MenuOutlined,
  MonitorOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  WifiOutlined,
}

const breadcrumbs = computed(() =>
  route.matched.filter((item) => item.meta.title).map((item) => item.meta.title as string),
)

watch(
  () => route.path,
  () => {
    const title = route.meta.title as string | undefined
    if (title) {
      appStore.addTab({ path: route.path, title })
    }
  },
  { immediate: true },
)

function hasChildren(menu: MenuNode): boolean {
  return Boolean(menu.children && menu.children.length > 0)
}

function iconOf(name?: string): Component {
  return (name && iconMap[name]) || ApiOutlined
}

function lastSegment(path: string): string {
  const parts = path.split('/').filter(Boolean)
  return parts[parts.length - 1] || ''
}

function fullPath(menu: MenuNode, parentPath = '/'): string {
  const path = menu.path || ''
  if (path.startsWith('/')) {
    return path
  }
  if (path === lastSegment(parentPath)) {
    return parentPath
  }
  return `${parentPath.replace(/\/$/, '')}/${path}`
}

function navigate(path: string) {
  router.push(path)
}

function onTabEdit(targetKey: string | MouseEvent | KeyboardEvent, action: 'add' | 'remove') {
  if (action !== 'remove' || typeof targetKey !== 'string') {
    return
  }
  appStore.removeTab(targetKey)
  if (route.path === targetKey) {
    const last = appStore.tabs[appStore.tabs.length - 1]
    router.push(last ? last.path : '/')
  }
}

async function onUserMenuClick({ key }: { key: string | number }) {
  if (key === 'profile') {
    router.push('/profile')
    return
  }
  if (key === 'logout') {
    await userStore.logout()
    message.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.app-logo {
  height: 48px;
  line-height: 48px;
  color: #fff;
  text-align: center;
  font-weight: 600;
}

.app-header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-entry {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.app-content {
  padding: 24px;
}

.app-tabs {
  margin-bottom: 16px;
}
</style>
