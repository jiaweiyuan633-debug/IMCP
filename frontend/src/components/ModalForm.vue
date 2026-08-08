<template>
  <a-modal
    :title="title"
    :width="width"
    :confirm-loading="loading"
    :open="open"
    @ok="emit('ok')"
    @cancel="emit('update:open', false)"
  >
    <slot />
  </a-modal>
</template>

<script setup lang="ts">
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
</script>
