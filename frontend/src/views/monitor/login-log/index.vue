<template>
  <a-card :title="t('page.monitorLoginTitle')">
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
import { getLoginLogPage } from '@/api/monitor'
import type { LoginLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useTableQuery } from '@/composables/useTableQuery'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [{ label: t('page.monitorUsername'), prop: 'username', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorUsername')}` }]

const columns = [
  { title: t('page.monitorUsername'), dataIndex: 'username', key: 'username' },
  { title: t('page.monitorIp'), dataIndex: 'ip', key: 'ip' },
  { title: t('page.monitorUserAgent'), dataIndex: 'userAgent', key: 'userAgent' },
  { title: t('page.monitorStatus'), key: 'status', width: 90 },
  { title: t('page.monitorMessage'), dataIndex: 'message', key: 'message' },
  { title: t('page.monitorLoginTime'), dataIndex: 'loginTime', key: 'loginTime' },
]

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<LoginLogVo>(getLoginLogPage, {
    buildParams: (query) => ({
      username: (query.username as string) || undefined,
    }),
  })
</script>


