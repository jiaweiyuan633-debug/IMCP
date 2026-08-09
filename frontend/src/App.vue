<template>
  <a-result v-if="appError" status="error" title="页面出现错误" sub-title="请点击重试恢复页面">
    <template #extra>
      <a-button type="primary" @click="appError = false">重试</a-button>
    </template>
  </a-result>
  <a-config-provider v-else :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed, onErrorCaptured, ref } from 'vue'
import { theme as antdTheme } from 'ant-design-vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const appError = ref(false)

onErrorCaptured(() => {
  appError.value = true
  return false
})

const themeConfig = computed(() => ({
  algorithm: appStore.darkTheme ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    borderRadius: 6,
  },
}))
</script>
