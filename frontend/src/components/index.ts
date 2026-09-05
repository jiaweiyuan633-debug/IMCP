/**
 * 自定义组件统一导出。
 *
 * 沉淀的自研组件与工具，统一从这里按需引入：
 * ```ts
 * import { ProTable, ProSearchForm, ModalForm, StatusTag, useTableQuery } from '@/components'
 * ```
 */
export { default as ProTable } from './ProTable.vue'
export { default as ProSearchForm } from './ProSearchForm.vue'
export { default as ModalForm } from './ModalForm.vue'
export { default as StatusTag } from './StatusTag.vue'
export { default as TableEmpty } from './TableEmpty.vue'
export { default as TableError } from './TableError.vue'
export { default as FileUpload } from './FileUpload.vue'
export { default as GlobalSearch } from './GlobalSearch.vue'

export { useTableQuery } from '@/composables/useTableQuery'
export type { TableQueryFetcher, UseTableQueryOptions } from '@/composables/useTableQuery'
export type { PageResult, SearchField } from '@/types'
