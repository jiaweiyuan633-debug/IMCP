<template>
  <a-result status="error" :title="t('common.loadFailed')" :sub-title="error?.message || ''">
    <template #extra>
      <a-button type="primary" @click="emit('retry')">{{ t('common.retry') }}</a-button>
    </template>
  </a-result>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{ error?: Error | null }>()

const emit = defineEmits<{ retry: [] }>()

const { t } = useI18n()
</script>
