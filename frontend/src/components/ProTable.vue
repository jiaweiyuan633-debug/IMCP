<template>
  <a-table
    :columns="columns"
    :data-source="dataSource"
    :loading="loading"
    :row-key="rowKey"
    :row-selection="rowSelection"
    :pagination="pagination"
    @change="onChange"
  >
    <!-- antd Table 的空态插槽是 emptyText（empty 无效），此处对接并保留对外 empty 插槽 -->
    <template #emptyText>
      <TableError v-if="error" :error="error" @retry="emit('retry')" />
      <slot name="empty" v-else />
    </template>
    <template #bodyCell="slotProps">
      <slot name="bodyCell" v-bind="slotProps" />
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import TableError from '@/components/TableError.vue'

const props = defineProps<{
  columns: unknown[]
  dataSource: unknown[]
  loading?: boolean
  total: number
  pageNum: number
  pageSize: number
  rowKey?: string
  /** 可选行选择配置（antd Table rowSelection），未传则表无选择列（向后兼容）。 */
  rowSelection?: Record<string, unknown>
  error?: Error | null
}>()

const emit = defineEmits<{
  'update:pageNum': [value: number]
  'update:pageSize': [value: number]
  change: []
  retry: []
}>()

const { t } = useI18n()

const pagination = computed(() => ({
  current: props.pageNum,
  pageSize: props.pageSize,
  total: props.total,
  showSizeChanger: true,
  showTotal: (total: number) => t('common.total', { total }),
}))

function onChange(paginationValue: { current?: number; pageSize?: number }) {
  emit('update:pageNum', paginationValue.current || 1)
  emit('update:pageSize', paginationValue.pageSize || 10)
  emit('change')
}
</script>
