<template>
  <a-card :title="t('page.aiTaskTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'ai:task:create'" type="primary" @click="openCreate">{{ t('page.aiCreate') }}</a-button>
    </div>
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
        <template v-else-if="column.key === 'retryCount'">
          {{ record.retryCount }} / {{ record.maxRetry }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a @click="openDetail(record)">{{ t('page.aiDetail') }}</a>
            <a v-permission="'ai:task:cancel'" @click="onCancel(record)">{{ t('page.aiCancel') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="createOpen"
      :title="t('page.aiCreateTitle')"
      :loading="creating"
      @ok="onCreate"
    >
      <a-form layout="vertical" :model="createForm">
        <a-form-item :label="t('page.aiBizType')" required>
          <a-select v-model:value="createForm.bizType" :options="bizTypeOptions" />
        </a-form-item>
        <a-form-item :label="t('page.aiParams')" required>
          <a-textarea v-model:value="createForm.paramsText" :rows="6" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="detailOpen" :title="t('page.aiDetail')" width="720" :footer="null">
      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item :label="t('page.aiTaskNoDetail')" :span="2">{{ detail?.taskNo }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.aiBizType')">{{ detail?.bizType }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.aiStatus')">
          <StatusTag v-if="detail" :value="detail.status" />
        </a-descriptions-item>
        <a-descriptions-item :label="t('page.aiRetry')">{{ detail ? `${detail.retryCount} / ${detail.maxRetry}` : '-' }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.aiCreatedAt')">{{ detail?.createdAt }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.aiError')" :span="2">{{ detail?.errorMsg || '-' }}</a-descriptions-item>
      </a-descriptions>
      <a-divider />
      <div class="result-title">{{ t('page.aiResult') }}</div>
      <pre class="result-box">{{ prettyResult }}</pre>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { cancelAiTask, createAiTask, getAiTaskDetail, getAiTaskPage } from '@/api/ai'
import type { AiTaskVo } from '@/api/ai'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const statusOptions = [
  { label: t('common.pending'), value: 'PENDING' },
  { label: t('common.queued'), value: 'QUEUED' },
  { label: t('common.running'), value: 'RUNNING' },
  { label: t('common.succeeded'), value: 'SUCCEEDED' },
  { label: t('common.failed'), value: 'FAILED' },
  { label: t('common.cancelled'), value: 'CANCELLED' },
]

const bizTypeOptions = [
  { label: 'Text Summary', value: 'text_summary' },
  { label: 'Keyword Extract', value: 'keyword_extract' },
]

const searchFields: SearchField[] = [
  { label: t('page.aiBizType'), prop: 'bizType', type: 'select', options: bizTypeOptions },
  { label: t('page.aiStatus'), prop: 'status', type: 'select', options: statusOptions },
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: t('page.aiTaskNo'), dataIndex: 'taskNo', key: 'taskNo' },
  { title: t('page.aiBizType'), dataIndex: 'bizType', key: 'bizType' },
  { title: t('page.aiStatus'), key: 'status', width: 100 },
  { title: t('page.aiRetry'), key: 'retryCount', width: 90 },
  { title: t('page.aiCreatedAt'), dataIndex: 'createdAt', key: 'createdAt' },
  { title: t('page.aiUpdatedAt'), dataIndex: 'updatedAt', key: 'updatedAt' },
  { title: t('page.aiActions'), key: 'actions', width: 130 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const creating = ref(false)
const records = ref<AiTaskVo[]>([])
const createOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<AiTaskVo | null>(null)
const searchModel = reactive<Record<string, unknown>>({})
const createForm = reactive({
  bizType: 'text_summary',
  paramsText: '{"content":"Please enter the text to analyze","max_length":200}',
})
let pollTimer: ReturnType<typeof setInterval> | null = null

const prettyResult = computed(() => {
  const raw = detail.value?.result?.resultJson
  if (!raw) {
    return t('page.aiNoResult')
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

async function loadData() {
  loading.value = true
  try {
    const data = await getAiTaskPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: searchModel.status as string | undefined,
      bizType: searchModel.bizType as string | undefined,
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

function openCreate() {
  createForm.bizType = 'text_summary'
  createForm.paramsText = '{"content":"Please enter the text to analyze","max_length":200}'
  createOpen.value = true
}

async function onCreate() {
  let params: Record<string, unknown>
  try {
    params = JSON.parse(createForm.paramsText)
  } catch {
    message.error(t('page.aiInvalidJson'))
    return
  }
  creating.value = true
  try {
    await createAiTask({ bizType: createForm.bizType, params })
    message.success(t('page.aiCreated'))
    createOpen.value = false
    loadData()
  } finally {
    creating.value = false
  }
}

async function openDetail(record: AiTaskVo) {
  detailOpen.value = true
  stopPolling()
  await loadDetail(record.id)
  if (detail.value && !isTerminal(detail.value.status)) {
    pollTimer = setInterval(() => loadDetail(record.id), 2000)
  }
}

async function loadDetail(id: number) {
  const data = await getAiTaskDetail(id)
  detail.value = data
  if (isTerminal(data.status)) {
    stopPolling()
  }
}

function isTerminal(status: string) {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function onCancel(record: AiTaskVo) {
  Modal.confirm({
    title: t('page.aiCancelTitle'),
    content: t('page.aiCancelConfirm', { name: record.taskNo }),
    onOk: async () => {
      await cancelAiTask(record.id)
      message.success(t('page.aiCancelled'))
      loadData()
    },
  })
}

onBeforeUnmount(stopPolling)
loadData()
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.result-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.result-box {
  max-height: 280px;
  overflow: auto;
  background: #f5f5f5;
  border-radius: 6px;
  padding: 12px;
  margin: 0;
}
</style>

