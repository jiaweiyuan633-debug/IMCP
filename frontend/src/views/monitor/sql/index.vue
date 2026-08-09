<template>
  <a-card :title="t('page.monitorSqlTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <ProTable
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :columns="columns"
      :data-source="records"
      :loading="loading"
      :total="total"
      :error="error"
      row-key="id"
      @change="loadData"
      @retry="loadData"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'success'">
          <StatusTag :value="record.success" />
        </template>
        <template v-else-if="column.key === 'durationMs'">
          {{ record.durationMs }} ms
        </template>
      </template>
    </ProTable>
  </a-card>
</template>

<script setup lang="ts">
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getSqlLogPage } from '@/api/monitor'
import type { SqlLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { useTableQuery } from '@/composables/useTableQuery'

const { t } = useI18n()

const searchFields: SearchField[] = [{ label: t('page.monitorSqlText'), prop: 'sqlText', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorSqlText')}` }]

const columns = [
  { title: t('page.monitorSqlText'), dataIndex: 'sqlText', key: 'sqlText' },
  { title: t('page.monitorMethod'), dataIndex: 'method', key: 'method', width: 220 },
  { title: t('page.monitorDuration'), key: 'durationMs', width: 100 },
  { title: t('page.monitorStatus'), key: 'success', width: 90 },
  { title: t('page.aiError'), dataIndex: 'errorMsg', key: 'errorMsg' },
  { title: t('page.workflowCreatedAt'), dataIndex: 'createdAt', key: 'createdAt', width: 180 },
]

const {
  pageNum,
  pageSize,
  total,
  loading,
  records,
  error,
  loadData,
  onSearch,
  onReset,
} = useTableQuery<SqlLogVo>(getSqlLogPage, {
  buildParams: (query) => ({ sqlText: (query.sqlText as string) || undefined }),
})
</script>

