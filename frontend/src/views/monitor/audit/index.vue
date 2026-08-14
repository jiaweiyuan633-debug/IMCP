<template>
  <a-card :title="t('page.monitorAuditTitle')">
    <a-tabs default-active-key="oper">
      <a-tab-pane key="oper" :tab="t('page.monitorAuditTab')">
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
      </a-tab-pane>
      <a-tab-pane key="field" :tab="t('page.monitorFieldAuditTab')">
        <ProSearchForm :fields="fieldSearchFields" :loading="fieldLoading" @search="onFieldSearch" @reset="onFieldReset" />
        <ProTable
          v-model:page-num="fieldPageNum"
          v-model:page-size="fieldPageSize"
          :columns="fieldColumns"
          :data-source="fieldRecords"
          :loading="fieldLoading"
          :total="fieldTotal"
          :error="fieldError"
          row-key="id"
          @change="loadFieldData"
          @retry="loadFieldData"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-tag :color="actionColor(record.action)">{{ record.action }}</a-tag>
            </template>
            <template v-else-if="column.key === 'changedFields'">
              <span class="ellipsis">{{ changeSummary(record) }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a @click="openDetail(record)">{{ t('page.monitorFieldAuditDetails') }}</a>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>
    </a-tabs>

    <a-drawer v-model:open="detailOpen" :title="t('page.monitorFieldAuditDetails')" width="720">
      <template v-if="detail">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item :label="t('page.monitorModule')">{{ detail.module }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.monitorFieldAuditAction')">{{ detail.action }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.monitorFieldAuditEntity')">{{ detail.entityName }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.monitorFieldAuditEntityId')">{{ detail.entityId }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.monitorOperTime')" :span="2">{{ formatDateTime(detail.createdAt) }}</a-descriptions-item>
        </a-descriptions>
        <h4 class="detail-title">{{ t('page.monitorFieldAuditChanged') }}</h4>
        <a-table
          v-if="detailChanges.length"
          :columns="changeColumns"
          :data-source="detailChanges"
          row-key="field"
          :pagination="false"
          size="small"
          bordered
        />
        <a-empty v-else :description="t('page.monitorFieldAuditEmpty')" />
      </template>
    </a-drawer>
  </a-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { exportAuditLogs, getAuditLogPage, getFieldAuditPage } from '@/api/monitor'
import type { AuditLogVo, FieldAuditLogVo, FieldChange } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useTableQuery } from '@/composables/useTableQuery'
import { useI18n } from 'vue-i18n'
import { dateColumn, formatDateTime } from '@/utils/table'

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
  dateColumn('createdAt', { title: t('page.workflowCreatedAt'), width: 180 }),
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

const fieldSearchFields: SearchField[] = [
  { label: t('page.monitorModule'), prop: 'module', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorModule')}` },
  { label: t('page.monitorFieldAuditEntity'), prop: 'entityName', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorFieldAuditEntity')}` },
]

const fieldColumns = [
  dateColumn('createdAt', { title: t('page.workflowCreatedAt'), width: 180 }),
  { title: t('page.monitorModule'), dataIndex: 'module', key: 'module', width: 120 },
  { title: t('page.monitorFieldAuditEntity'), dataIndex: 'entityName', key: 'entityName', width: 140 },
  { title: t('page.monitorFieldAuditEntityId'), dataIndex: 'entityId', key: 'entityId', width: 100 },
  { title: t('page.monitorFieldAuditAction'), key: 'action', width: 90 },
  { title: t('page.monitorFieldAuditChanged'), key: 'changedFields', ellipsis: true },
  { title: t('common.actions'), key: 'actions', width: 90 },
]

const changeColumns = [
  { title: t('page.monitorFieldAuditField'), dataIndex: 'label', key: 'label', width: 140 },
  { title: t('page.monitorFieldAuditBefore'), dataIndex: 'before', key: 'before', ellipsis: true },
  { title: t('page.monitorFieldAuditAfter'), dataIndex: 'after', key: 'after', ellipsis: true },
]

const { pageNum: fieldPageNum, pageSize: fieldPageSize, total: fieldTotal, loading: fieldLoading, records: fieldRecords, error: fieldError, loadData: loadFieldData, onSearch: onFieldSearch, onReset: onFieldReset } =
  useTableQuery<FieldAuditLogVo>(getFieldAuditPage, {
    buildParams: (query) => ({
      module: (query.module as string) || undefined,
      entityName: (query.entityName as string) || undefined,
    }),
  })

function parseChanges(raw?: string): FieldChange[] {
  if (!raw) {
    return []
  }
  try {
    return JSON.parse(raw) as FieldChange[]
  } catch {
    return []
  }
}

function changeSummary(record: FieldAuditLogVo): string {
  const changes = parseChanges(record.changedFields)
  return changes.length ? `${changes.length} ${t('page.monitorFieldAuditChanged')}` : '-'
}

function actionColor(action?: string): string {
  if (action === 'CREATE') return 'green'
  if (action === 'DELETE') return 'red'
  return 'blue'
}

const detailOpen = ref(false)
const detail = ref<FieldAuditLogVo | null>(null)
const detailChanges = computed(() => parseChanges(detail.value?.changedFields))

function openDetail(record: FieldAuditLogVo) {
  detail.value = record
  detailOpen.value = true
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.ellipsis {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.detail-title {
  margin: 16px 0 8px;
}
</style>
