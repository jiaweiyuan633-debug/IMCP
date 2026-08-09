<template>
  <a-tag :color="color">{{ text }}</a-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: number | string
}>()

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
    '0': '禁用',
    '1': '启用',
    PENDING: '待处理',
    QUEUED: '排队中',
    RUNNING: '执行中',
    SUCCEEDED: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
  }
  return map[value] || value
})
</script>

