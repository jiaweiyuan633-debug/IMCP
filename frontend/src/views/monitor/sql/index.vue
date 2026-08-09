<template>
  <a-card title="SQL 监控">
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

const searchFields: SearchField[] = [{ label: 'SQL 内容', prop: 'sqlText', placeholder: '请输入 SQL 内容' }]

const columns = [
  { title: 'SQL', dataIndex: 'sqlText', key: 'sqlText' },
  { title: '方法', dataIndex: 'method', key: 'method', width: 220 },
  { title: '耗时', key: 'durationMs', width: 100 },
  { title: '结果', key: 'success', width: 90 },
  { title: '错误', dataIndex: 'errorMsg', key: 'errorMsg' },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
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

