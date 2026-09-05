<template>
  <a-card :title="t('page.noticeChannelLogTitle')">
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
        <template v-if="column.key === 'channelType'">
          <a-tag :color="typeColor(record.channelType)">{{ typeLabel(record.channelType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'title'">
          <span :title="record.content || ''">{{ record.title }}</span>
        </template>
        <template v-else-if="column.key === 'errorMsg'">
          <a-tooltip v-if="record.errorMsg" :title="record.errorMsg">
            <span class="error-text">{{ record.errorMsg }}</span>
          </a-tooltip>
          <span v-else>-</span>
        </template>
      </template>
    </ProTable>
  </a-card>
</template>

<script setup lang="ts">
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { getChannelLogPage } from '@/api/channel'
import type { ChannelLogVo } from '@/api/channel'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const channelTypeOptions = [
  { label: t('page.noticeChannelTypeMail'), value: 'MAIL' },
  { label: t('page.noticeChannelTypeSms'), value: 'SMS' },
  { label: t('page.noticeChannelTypeDingtalk'), value: 'DINGTALK' },
  { label: t('page.noticeChannelTypeWecom'), value: 'WECOM' },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const TYPE_COLORS: Record<string, string> = {
  MAIL: 'blue',
  SMS: 'orange',
  DINGTALK: 'cyan',
  WECOM: 'green',
}

const TYPE_LABELS: Record<string, () => string> = {
  MAIL: () => t('page.noticeChannelTypeMail'),
  SMS: () => t('page.noticeChannelTypeSms'),
  DINGTALK: () => t('page.noticeChannelTypeDingtalk'),
  WECOM: () => t('page.noticeChannelTypeWecom'),
}

function typeLabel(type: string): string {
  return TYPE_LABELS[type]?.() ?? type
}

function typeColor(type: string): string {
  return TYPE_COLORS[type] || 'default'
}

const searchFields: SearchField[] = [
  {
    label: t('page.noticeChannelType'),
    prop: 'channelType',
    type: 'select',
    options: channelTypeOptions,
  },
  {
    label: t('page.noticeChannelStatus'),
    prop: 'status',
    type: 'select',
    options: statusOptions,
  },
]

const columns = [
  { title: t('page.noticeChannelType'), dataIndex: 'channelType', key: 'channelType', width: 110 },
  { title: t('page.noticeChannelTarget'), dataIndex: 'target', key: 'target', width: 160, ellipsis: true },
  { title: t('page.noticeChannelTitleField'), dataIndex: 'title', key: 'title', ellipsis: true },
  { title: t('page.noticeChannelStatus'), dataIndex: 'status', key: 'status', width: 80 },
  { title: t('page.noticeChannelError'), dataIndex: 'errorMsg', key: 'errorMsg', width: 200, ellipsis: true },
  dateColumn('createdAt', { title: t('page.noticeChannelTime'), width: 170 }),
]

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ChannelLogVo>(getChannelLogPage, {
    buildParams: (query) => ({
      channelType: (query.channelType as string) || undefined,
      status: query.status as number | undefined,
    }),
  })
</script>

<style scoped>
.error-text {
  color: #ff4d4f;
}
</style>
