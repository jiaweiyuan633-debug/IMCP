<template>
  <a-modal v-model:open="visible" :title="t('layout.searchMenu')" :footer="null" width="520" :centered="true">
    <a-input
      v-model:value="keyword"
      :placeholder="t('layout.searchPlaceholder')"
      allow-clear
      autofocus
      @keydown.enter="goFirst"
    />
    <a-list v-if="filtered.length" class="search-list" size="small" :data-source="filtered">
      <template #renderItem="{ item }">
        <a-list-item class="search-item" @click="go(item)">
          <span>{{ item.title }}</span>
          <span class="search-path">{{ item.path }}</span>
        </a-list-item>
      </template>
    </a-list>
    <a-empty v-else :description="t('layout.searchEmpty')" />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import type { MenuNode } from '@/types'
import { useI18n } from 'vue-i18n'
import { fullPathOf } from '@/utils/menuPath'

interface SearchItem {
  title: string
  path: string
}

const { t } = useI18n()
const router = useRouter()
const permissionStore = usePermissionStore()
const visible = ref(false)
const keyword = ref('')

const filtered = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  const items = flatten(permissionStore.menus)
  if (!value) {
    return items.slice(0, 10)
  }
  return items.filter((item) => item.title.toLowerCase().includes(value) || item.path.toLowerCase().includes(value)).slice(0, 10)
})

function flatten(menus: MenuNode[], parentPath = '/'): SearchItem[] {
  const result: SearchItem[] = []
  for (const menu of menus) {
    const path = fullPathOf(menu, parentPath)
    if (menu.type === 'dir') {
      result.push(...flatten(menu.children || [], path))
    } else if (menu.type === 'menu' && menu.status === 1 && menu.visible === 1) {
      result.push({ title: menu.name, path })
    }
  }
  return result
}

function go(item: SearchItem) {
  visible.value = false
  keyword.value = ''
  router.push(item.path)
}

function goFirst() {
  if (filtered.value.length > 0) {
    go(filtered.value[0])
  }
}

function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    visible.value = true
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.search-list {
  margin-top: 12px;
  max-height: 360px;
  overflow: auto;
}

.search-item {
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.search-path {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
