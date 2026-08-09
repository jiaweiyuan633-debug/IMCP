<template>
  <a-card :title="t('page.monitorAuditTitle')">
    <div class="toolbar">
      <a-button @click="onExport">{{ t('page.monitorAuditExport') }}</a-button>
    </div>
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
        <template v-else-if="column.key === 'params' || column.key === 'result'">
          <a-tooltip :title="String(record[column.key] || '')">
            <span class="ellipsis">{{ record[column.key] || '-' }}</span>
          </a-tooltip>
        </template>
      </template>
    </ProTable>
  </a-card>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { exportAuditLogs, getAuditLogPage } from '@/api/monitor'
import type { AuditLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useTableQuery } from '@/composables/useTableQuery'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.monitorModule'), prop: 'module', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorModule')}` },
]

const columns = [
  { title: t('page.monitorModule'), dataIndex: 'module', key: 'module', width: 120 },
  { title: t('page.monitorAction'), dataIndex: 'action', key: 'action', width: 120 },
  { title: t('page.monitorRequestUrl'), dataIndex: 'params', key: 'params', ellipsis: true },
  { title: t('page.aiResult'), dataIndex: 'result', key: 'result', ellipsis: true },
  { title: t('page.monitorStatus'), key: 'status', width: 90 },
  { title: t('page.workflowCreatedAt'), dataIndex: 'createdAt', key: 'createdAt', width: 180 },
]

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<AuditLogVo>(getAuditLogPage, {
    buildParams: (query) => ({
      module: (query.module as string) || undefined,
    }),
  })

async function onExport() {
  await exportAuditLogs()
  message.success(t('page.monitorAuditExported'))
}
</script>

<style scoped>
.ellipsis {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.toolbar {
  margin-bottom: 16px;
}
</style>
