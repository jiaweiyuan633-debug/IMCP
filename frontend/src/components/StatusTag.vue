<template>
  <a-tag :color="color">{{ text }}</a-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  value: number | string
}>()
const { t } = useI18n()

const color = computed(() => {
  const value = String(props.value)
  if (value === '1' || value === 'SUCCEEDED' || value === 'success') {
    return 'success'
  }
  if (value === '0' || value === 'FAILED' || value === 'error') {
    return 'error'
  }
  if (['PENDING', 'QUEUED', 'RUNNING', 'processing'].includes(value)) {
    return 'processing'
  }
  if (value === 'CANCELLED' || value === 'warning') {
    return 'warning'
  }
  return 'default'
})

const text = computed(() => {
  const value = String(props.value)
  const map: Record<string, string> = {
    '0': t('common.disabled'),
    '1': t('common.enabled'),
    PENDING: t('common.pending'),
    QUEUED: t('common.queued'),
    RUNNING: t('common.running'),
    SUCCEEDED: t('common.succeeded'),
    FAILED: t('common.failed'),
    CANCELLED: t('common.cancelled'),
    APPROVED: t('common.approved'),
    REJECTED: t('common.rejected'),
    WITHDRAWN: t('common.withdrawn'),
  }
  return map[value] || value
})
</script>

