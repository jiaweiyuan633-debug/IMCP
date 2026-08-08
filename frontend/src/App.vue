<template>
  <a-config-provider :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { theme as antdTheme } from 'ant-design-vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

const themeConfig = computed(() => ({
  algorithm: appStore.darkTheme ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    borderRadius: 6,
  },
}))
</script>
