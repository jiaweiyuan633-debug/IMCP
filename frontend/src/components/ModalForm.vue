<template>
  <a-modal
    :title="title"
    :width="width"
    :confirm-loading="loading"
    :open="open"
    :ok-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    @ok="emit('ok')"
    @cancel="emit('update:open', false)"
  >
    <slot />
  </a-modal>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  open: boolean
  title: string
  width?: number | string
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  ok: []
}>()

const { t } = useI18n()
</script>
