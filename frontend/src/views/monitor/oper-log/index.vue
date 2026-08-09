<template>
  <a-card :title="t('page.monitorOperTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
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
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
      </template>
    </ProTable>
  </a-card>
</template>

<script setup lang="ts">
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getOperLogPage } from '@/api/monitor'
import type { OperLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { useTableQuery } from '@/composables/useTableQuery'

const { t } = useI18n()

const searchFields: SearchField[] = [{ label: t('page.monitorModule'), prop: 'module', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorModule')}` }]

const columns = [
  { title: t('page.monitorModule'), dataIndex: 'module', key: 'module' },
  { title: t('page.monitorAction'), dataIndex: 'action', key: 'action' },
  { title: t('page.monitorRequestUrl'), dataIndex: 'requestMethod', key: 'requestMethod', width: 90 },
  { title: t('page.monitorRequestUrl'), dataIndex: 'requestUrl', key: 'requestUrl' },
  { title: t('page.monitorStatus'), key: 'status', width: 90 },
  { title: t('page.monitorDuration'), dataIndex: 'durationMs', key: 'durationMs', width: 100 },
  { title: t('page.monitorIp'), dataIndex: 'ip', key: 'ip', width: 120 },
  { title: t('page.monitorOperTime'), dataIndex: 'operTime', key: 'operTime' },
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
} = useTableQuery<OperLogVo>(getOperLogPage, {
  buildParams: (query) => ({ module: (query.module as string) || undefined }),
})
</script>


