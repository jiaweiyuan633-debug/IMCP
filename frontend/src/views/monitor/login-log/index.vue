<template>
  <a-card title="登录日志">
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

const searchFields: SearchField[] = [{ label: '用户名', prop: 'username', placeholder: '请输入用户名' }]

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '浏览器', dataIndex: 'userAgent', key: 'userAgent' },
  { title: '结果', key: 'status', width: 90 },
  { title: '说明', dataIndex: 'message', key: 'message' },
  { title: '登录时间', dataIndex: 'loginTime', key: 'loginTime' },
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


