<template>
  <a-card :title="t('page.componentDemoTitle')">
    <a-alert
      class="demo-intro"
      :message="t('page.componentDemoIntro')"
      type="info"
      show-icon
    />

    <a-divider orientation="left">{{ t('page.componentDemoStatusTag') }}</a-divider>
    <a-space wrap size="large">
      <StatusTag :value="1" />
      <StatusTag :value="0" />
    </a-space>

    <a-divider orientation="left">{{ t('page.componentDemoProTable') }}</a-divider>
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
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
      </template>
    </ProTable>

    <a-divider orientation="left">{{ t('page.componentDemoModalForm') }}</a-divider>
    <a-space wrap>
      <a-button type="primary" @click="openDemoModal">{{ t('page.componentDemoOpenModal') }}</a-button>
      <ModalForm v-model:open="demoModalOpen" :title="t('page.componentDemoModalForm')" :loading="demoSaving" @ok="onDemoSubmit">
        <a-form layout="vertical" :model="demoForm">
          <a-form-item :label="t('page.componentDemoRowName')">
            <a-input v-model:value="demoForm.name" />
          </a-form-item>
        </a-form>
      </ModalForm>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { ProSearchForm, ProTable, ModalForm, StatusTag, useTableQuery } from '@/components'
import type { PageResult, SearchField } from '@/types'

const { t } = useI18n()

interface DemoRow {
  id: number
  name: string
  type: string
  status: number
}

/** 本地 mock 数据：模拟服务端分页 + 关键字过滤，便于演示组件交互 */
const demoData: DemoRow[] = Array.from({ length: 28 }, (_, i) => ({
  id: i + 1,
  name: `${t('page.componentDemoRowName')} ${i + 1}`,
  type: ['A', 'B', 'C'][i % 3],
  status: i % 3 === 0 ? 0 : 1,
}))

function mockFetch(params: Record<string, unknown> & { pageNum: number; pageSize: number }): Promise<PageResult<DemoRow>> {
  return new Promise((resolve) => {
    setTimeout(() => {
      const keyword = String(params.keyword || '').trim()
      const filtered = keyword
        ? demoData.filter((row) => row.name.includes(keyword))
        : [...demoData]
      const start = (params.pageNum - 1) * params.pageSize
      resolve({
        records: filtered.slice(start, start + params.pageSize),
        total: filtered.length,
        pageNum: params.pageNum,
        pageSize: params.pageSize,
      })
    }, 200)
  })
}

const searchFields: SearchField[] = [
  {
    label: t('page.componentDemoRowName'),
    prop: 'keyword',
    placeholder: `${t('common.inputPlaceholder')}${t('page.componentDemoRowName')}`,
  },
]

const columns = [
  { title: t('page.componentDemoRowName'), dataIndex: 'name', key: 'name' },
  { title: t('page.componentDemoRowType'), dataIndex: 'type', key: 'type', width: 120 },
  { title: t('page.componentDemoRowStatus'), dataIndex: 'status', key: 'status', width: 100 },
]

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } = useTableQuery<
  DemoRow,
  Record<string, unknown>
>(mockFetch)

const demoModalOpen = ref(false)
const demoSaving = ref(false)
const demoForm = ref({ name: '' })

function openDemoModal() {
  demoForm.value = { name: '' }
  demoModalOpen.value = true
}

function onDemoSubmit() {
  demoSaving.value = true
  setTimeout(() => {
    demoSaving.value = false
    demoModalOpen.value = false
    message.success(t('page.componentDemoSubmit'))
  }, 400)
}
</script>

<style scoped>
.demo-intro {
  margin-bottom: 8px;
}
</style>
