<template>
  <a-result v-if="appError" status="error" :title="t('page.appError')" :sub-title="t('page.appErrorRetry')">
    <template #extra>
      <a-button type="primary" @click="appError = false">{{ t('common.retry') }}</a-button>
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
import { useI18n } from 'vue-i18n'

const appStore = useAppStore()
const { t } = useI18n()
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
