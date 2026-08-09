<template>
  <a-card :title="t('page.monitorAuditTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <ProTable
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :columns="columns"
      :data-source="records"
      :loading="loading"
      :total="total"
      row-key="id"
      @change="loadData"
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
import { reactive, ref } from 'vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getAuditLogPage } from '@/api/monitor'
import type { AuditLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'
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

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const records = ref<AuditLogVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})

async function loadData() {
  loading.value = true
  try {
    const data = await getAuditLogPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      module: (searchModel.module as string) || undefined,
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onSearch(model: Record<string, unknown>) {
  Object.assign(searchModel, model)
  pageNum.value = 1
  loadData()
}

function onReset() {
  Object.keys(searchModel).forEach((key) => {
    searchModel[key] = undefined
  })
  pageNum.value = 1
  loadData()
}

loadData()
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
</style>
