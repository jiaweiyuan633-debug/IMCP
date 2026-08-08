<template>
  <a-table
    :columns="columns"
    :data-source="dataSource"
    :loading="loading"
    :row-key="rowKey"
    :pagination="pagination"
    @change="onChange"
  >
    <template #bodyCell="slotProps">
      <slot name="bodyCell" v-bind="slotProps" />
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  columns: unknown[]
  dataSource: unknown[]
  loading?: boolean
  total: number
  pageNum: number
  pageSize: number
  rowKey?: string
}>()

const emit = defineEmits<{
  'update:pageNum': [value: number]
  'update:pageSize': [value: number]
  change: []
}>()

const pagination = computed(() => ({
  current: props.pageNum,
  pageSize: props.pageSize,
  total: props.total,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
}))

function onChange(paginationValue: { current?: number; pageSize?: number }) {
  emit('update:pageNum', paginationValue.current || 1)
  emit('update:pageSize', paginationValue.pageSize || 10)
  emit('change')
}
</script>

