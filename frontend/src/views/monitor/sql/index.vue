<template>
  <a-card :title="t('page.monitorSqlTitle')">
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
import { reactive, ref } from 'vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getSqlLogPage } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

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

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const records = ref<Record<string, unknown>[]>([])
const searchModel = reactive<Record<string, unknown>>({})

async function loadData() {
  loading.value = true
  try {
    const data = await getSqlLogPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sqlText: (searchModel.sqlText as string) || undefined,
    })
    records.value = data.records as Record<string, unknown>[]
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

