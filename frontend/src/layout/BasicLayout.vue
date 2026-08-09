<template>
  <a-layout class="app-layout" style="min-height: 100vh">
    <a class="skip-link" href="#main-content">{{ t('layout.skip') }}</a>
    <a-alert v-if="offline" banner type="warning" :message="t('layout.offline')" />
    <a-layout-sider class="desktop-sider" v-model:collapsed="appStore.collapsed" :width="220" theme="dark" collapsible :trigger="null">
      <div class="app-logo">
        <ApiOutlined v-if="appStore.collapsed" />
        <template v-else>{{ t('app.title') }}</template>
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
    <a-drawer
      v-model:open="mobileDrawer"
      placement="left"
      :width="220"
      :closable="false"
      :styles="{ body: { padding: 0, background: '#001529' } }"
    >
      <div class="app-logo">{{ t('app.title') }}</div>
      <a-menu :selected-keys="[route.path]" theme="dark" mode="inline" @click="onMenuNavigate">
        <template v-for="menu in permissionStore.menus" :key="fullPath(menu)">
          <a-sub-menu v-if="hasChildren(menu)" :key="fullPath(menu)">
            <template #title>
              <component :is="iconOf(menu.icon)" />
              <span>{{ menu.name }}</span>
            </template>
            <a-menu-item
              v-for="child in menu.children"
              :key="fullPath(child, fullPath(menu))"
            >
              <component :is="iconOf(child.icon)" />
              <span>{{ child.name }}</span>
            </a-menu-item>
          </a-sub-menu>
          <a-menu-item v-else :key="fullPath(menu)">
            <component :is="iconOf(menu.icon)" />
            <span>{{ menu.name }}</span>
          </a-menu-item>
        </template>
      </a-menu>
    </a-drawer>
    <a-layout>
      <a-layout-header class="app-header">
        <div class="header-left">
          <a-button type="text" @click="appStore.toggleCollapsed()">
            <MenuUnfoldOutlined v-if="appStore.collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <a-button v-if="isMobile" type="text" class="mobile-menu-button" @click="mobileDrawer = true">
            <MenuOutlined />
          </a-button>
          <a-breadcrumb class="header-breadcrumb">
            <a-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="header-right">
          <a-button type="text" @click="appStore.toggleTheme()">
            <BulbOutlined v-if="!appStore.darkTheme" />
            <BulbFilled v-else />
          </a-button>
          <a-dropdown>
            <a-button type="text">
              <GlobalOutlined />
            </a-button>
            <template #overlay>
              <a-menu @click="onLanguageClick">
                <a-menu-item key="zh-CN">中文</a-menu-item>
                <a-menu-item key="en-US">English</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-dropdown>
            <a-badge :count="unreadCount">
              <a-button type="text">
                <BellOutlined />
              </a-button>
            </a-badge>
            <template #overlay>
              <a-menu class="notice-menu" @click="onMessageClick">
                <a-menu-item v-for="item in noticeItems" :key="`${item.kind}-${item.id}`">
                  <div class="notice-item">
                    <span class="notice-title">
                      <a-tag :color="item.kind === 'notice' ? 'blue' : 'green'" style="margin-right: 4px">{{ item.tag }}</a-tag>
                      {{ item.title }}
                    </span>
                    <span class="notice-time">{{ formatTime(item.createdAt) }}</span>
                  </div>
                </a-menu-item>
                <a-menu-item v-if="noticeItems.length === 0" disabled>{{ t('common.noNotices') }}</a-menu-item>
                <a-menu-divider v-if="noticeItems.length > 0" />
                <a-menu-item key="mark-all">{{ t('page.noticeMarkAllRead') }}</a-menu-item>
                <a-menu-item key="view-all">{{ t('page.noticeViewAll') }}</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-dropdown>
            <span class="user-entry">
              <UserOutlined />
              {{ userStore.userInfo?.nickname || userStore.userInfo?.username }}
            </span>
            <template #overlay>
              <a-menu @click="onUserMenuClick">
                <a-menu-item key="profile">{{ t('layout.profile') }}</a-menu-item>
                <a-menu-item key="logout">{{ t('layout.logout') }}</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <a-layout-content id="main-content" class="app-content">
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
    <GlobalSearch />
  </a-layout>
</template>

<script setup lang="ts">
import {
  AlertOutlined,
  ApiOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  BulbFilled,
  BulbOutlined,
  CarryOutOutlined,
  ClusterOutlined,
  CodeOutlined,
  ControlOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  FieldTimeOutlined,
  FileTextOutlined,
  FolderOutlined,
  FundOutlined,
  GlobalOutlined,
  HistoryOutlined,
  IdcardOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MonitorOutlined,
  NotificationOutlined,
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
import { useI18n } from 'vue-i18n'
import { onMounted, onUnmounted, ref } from 'vue'
import { getAccessToken } from '@/utils/auth'
import {
  getLatestMessages,
  getLatestNotices,
  getNoticeSseTicket,
  getUnreadMessageCount,
  getUnreadNoticeCount,
  markAllMessageRead,
  markAllNoticeRead,
  markMessageRead,
  markNoticeRead,
} from '@/api/system'
import type { MessageVo, NoticeVo } from '@/api/system'
import { API_BASE_URL } from '@/utils/env'
import dayjs from 'dayjs'
import GlobalSearch from '@/components/GlobalSearch.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const permissionStore = usePermissionStore()
const userStore = useUserStore()
const { t, locale } = useI18n()
const latestMessages = ref<MessageVo[]>([])
const latestNotices = ref<NoticeVo[]>([])
const unreadCount = ref(0)
let noticeStream: EventSource | null = null
let messageSocket: WebSocket | null = null
const mobileDrawer = ref(false)
const isMobile = ref(false)
const offline = ref(false)

function onResize() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    mobileDrawer.value = false
  }
}

function onOffline() {
  offline.value = true
}

function onOnline() {
  offline.value = false
}

onMounted(async () => {
  onResize()
  window.addEventListener('resize', onResize)
  window.addEventListener('offline', onOffline)
  window.addEventListener('online', onOnline)
  await refreshNoticeItems()
  unreadCount.value = await getUnreadTotal()
  startNoticeStream()
  startMessageSocket()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('offline', onOffline)
  window.removeEventListener('online', onOnline)
  noticeStream?.close()
  noticeStream = null
  messageSocket?.close()
  messageSocket = null
})

const iconMap: Record<string, Component> = {
  AlertOutlined,
  ApiOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BarChartOutlined,
  BookOutlined,
  CarryOutOutlined,
  ClusterOutlined,
  CodeOutlined,
  ControlOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  FieldTimeOutlined,
  FileTextOutlined,
  FolderOutlined,
  FundOutlined,
  HistoryOutlined,
  IdcardOutlined,
  MenuOutlined,
  MonitorOutlined,
  NotificationOutlined,
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
    document.title = title ? `${title} - ${t('app.title')}` : t('app.title')
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

function onMenuNavigate({ key }: { key: string | number }) {
  navigate(String(key))
  mobileDrawer.value = false
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
    appStore.resetTabs()
    message.success(t('layout.logout'))
    router.push('/login')
  }
}

function onLanguageClick({ key }: { key: string | number }) {
  appStore.setLocale(String(key))
  locale.value = String(key)
}

function formatTime(value?: string): string {
  return value ? dayjs(value).format('MM-DD HH:mm') : ''
}

async function onMessageClick({ key }: { key: string | number }) {
  const value = String(key)
  if (value.startsWith('message-')) {
    const id = Number(value.replace('message-', ''))
    await markMessageRead(id)
    unreadCount.value = Math.max(unreadCount.value - 1, 0)
    await refreshNoticeItems()
    router.push({ path: '/system/message', query: { id } })
    return
  }
  if (value.startsWith('notice-')) {
    const id = Number(value.replace('notice-', ''))
    await markNoticeRead(id)
    unreadCount.value = Math.max(unreadCount.value - 1, 0)
    await refreshNoticeItems()
    router.push({ path: '/system/notice', query: { id } })
    return
  }
  if (value === 'mark-all') {
    await Promise.all([markAllMessageRead(), markAllNoticeRead()])
    unreadCount.value = 0
    await refreshNoticeItems()
    return
  }
  if (value === 'view-all') {
    router.push('/system/message')
  }
}

const noticeItems = computed(() => {
  const messages = latestMessages.value.map((item) => ({
    kind: 'message' as const,
    id: item.id,
    title: item.title,
    content: item.content,
    createdAt: item.createdAt,
    tag: t('page.messageTitle'),
  }))
  const notices = latestNotices.value.map((item) => ({
    kind: 'notice' as const,
    id: item.id,
    title: item.noticeTitle,
    content: item.noticeContent,
    createdAt: item.createdAt,
    tag: item.noticeType === 1 ? t('page.noticeNotice') : t('page.noticeAnnounce'),
  }))
  return [...messages, ...notices]
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
    .slice(0, 8)
})

async function refreshNoticeItems() {
  const [messages, notices] = await Promise.all([getLatestMessages(), getLatestNotices()])
  latestMessages.value = messages
  latestNotices.value = notices
}

async function getUnreadTotal(): Promise<number> {
  const [notices, messages] = await Promise.all([
    getUnreadNoticeCount(),
    getUnreadMessageCount(),
  ])
  return notices + messages
}

function startNoticeStream() {
  if (noticeStream) {
    return
  }
  getNoticeSseTicket()
    .then((ticket) => {
      const source = new EventSource(`${API_BASE_URL}/system/notice/stream?ticket=${encodeURIComponent(ticket)}`)
      source.addEventListener('notice', async () => {
        await refreshNoticeItems()
        unreadCount.value = await getUnreadTotal()
      })
      source.onerror = () => {
        source.close()
        noticeStream = null
      }
      noticeStream = source
    })
    .catch(() => {
      noticeStream = null
    })
}

function startMessageSocket() {
  const token = getAccessToken()
  if (!token) {
    return
  }
  try {
    const wsBase = API_BASE_URL.replace(/^http/, 'ws').replace(/\/api\/?$/, '')
    const socket = new WebSocket(`${wsBase}/ws/messages?token=${encodeURIComponent(token)}`)
    socket.onmessage = async () => {
      await refreshNoticeItems()
      unreadCount.value = await getUnreadTotal()
    }
    socket.onclose = () => {
      messageSocket = null
    }
    socket.onerror = () => {
      socket.close()
      messageSocket = null
    }
    messageSocket = socket
  } catch {
    messageSocket = null
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

.notice-menu {
  max-width: 320px;
}

.notice-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.notice-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-time {
  color: #8c8c8c;
  flex-shrink: 0;
  font-size: 12px;
}

.app-content {
  padding: 24px;
}

.app-tabs {
  margin-bottom: 16px;
}

@media (max-width: 767px) {
  .desktop-sider {
    display: none;
  }

  .header-breadcrumb,
  .app-tabs {
    display: none;
  }

  .app-content {
    padding: 12px;
  }

  .header-left {
    gap: 4px;
  }

  .app-header {
    padding: 0 8px;
  }
}

.skip-link {
  position: fixed;
  top: -40px;
  left: 12px;
  z-index: 1000;
  padding: 8px 14px;
  background: var(--brand, #2563eb);
  color: #fff;
  border-radius: 0 0 8px 8px;
  transition: top 0.15s ease;
}

.skip-link:focus {
  top: 0;
}
</style>
