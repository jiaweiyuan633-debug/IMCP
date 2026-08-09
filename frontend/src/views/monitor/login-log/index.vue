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
      row-key="id"
      @change="loadData"
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
import { reactive, ref } from 'vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getLoginLogPage } from '@/api/monitor'
import type { LoginLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'
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

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const records = ref<LoginLogVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})

async function loadData() {
  loading.value = true
  try {
    const data = await getLoginLogPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      username: (searchModel.username as string) || undefined,
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


