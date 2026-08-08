<template>
  <a-card title="操作日志">
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
import { getOperLogPage } from '@/api/monitor'
import type { OperLogVo } from '@/api/monitor'
import type { SearchField } from '@/types'

const searchFields: SearchField[] = [{ label: '模块', prop: 'module', placeholder: '请输入模块' }]

const columns = [
  { title: '模块', dataIndex: 'module', key: 'module' },
  { title: '操作', dataIndex: 'action', key: 'action' },
  { title: '请求方式', dataIndex: 'requestMethod', key: 'requestMethod', width: 90 },
  { title: '请求地址', dataIndex: 'requestUrl', key: 'requestUrl' },
  { title: '结果', key: 'status', width: 90 },
  { title: '耗时(ms)', dataIndex: 'durationMs', key: 'durationMs', width: 100 },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 120 },
  { title: '操作时间', dataIndex: 'operTime', key: 'operTime' },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const records = ref<OperLogVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})

async function loadData() {
  loading.value = true
  try {
    const data = await getOperLogPage({
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
  pageNum.value = 1
  loadData()
}

loadData()
</script>


