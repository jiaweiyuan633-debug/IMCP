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
      :error="error"
      row-key="id"
      @change="loadData"
      @retry="loadData"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'errorType'">
          <a-tag v-if="record.errorType" :color="errorTypeColor[record.errorType] || 'default'">
            {{ errorTypeLabel(record.errorType) }}
          </a-tag>
          <span v-else>-</span>
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
        <a-descriptions-item :label="t('page.aiErrorType')">
          <a-tag v-if="detail?.errorType" :color="errorTypeColor[detail.errorType] || 'default'">{{ errorTypeLabel(detail.errorType) }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
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
import { cancelAiTask, createAiTask, getAiSseTicket, getAiTaskDetail, getAiTaskPage } from '@/api/ai'
import type { AiTaskVo } from '@/api/ai'
import type { SearchField } from '@/types'
import { useTableQuery } from '@/composables/useTableQuery'
import { useI18n } from 'vue-i18n'
import { API_BASE_URL } from '@/utils/env'

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

// R4-1.23：失败分类（R4-1.20 回调落库 error_type）——瞬时超时值得重试，确定性错误重试无意义
const errorTypeOptions = [
  { label: t('page.aiErrorTypeTimeout'), value: 'timeout' },
  { label: t('page.aiErrorTypeNonRetryable'), value: 'non_retryable' },
  { label: t('page.aiErrorTypeRetriesExhausted'), value: 'retries_exhausted' },
]

const errorTypeColor: Record<string, string> = {
  timeout: 'orange',
  non_retryable: 'red',
  retries_exhausted: 'purple',
}

function errorTypeLabel(value: string): string {
  const map: Record<string, string> = {
    timeout: t('page.aiErrorTypeTimeout'),
    non_retryable: t('page.aiErrorTypeNonRetryable'),
    retries_exhausted: t('page.aiErrorTypeRetriesExhausted'),
  }
  return map[value] || value
}

const searchFields: SearchField[] = [
  { label: t('page.aiBizType'), prop: 'bizType', type: 'select', options: bizTypeOptions },
  { label: t('page.aiStatus'), prop: 'status', type: 'select', options: statusOptions },
  { label: t('page.aiErrorType'), prop: 'errorType', type: 'select', options: errorTypeOptions },
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: t('page.aiTaskNo'), dataIndex: 'taskNo', key: 'taskNo' },
  { title: t('page.aiBizType'), dataIndex: 'bizType', key: 'bizType' },
  { title: t('page.aiStatus'), key: 'status', width: 100 },
  { title: t('page.aiErrorType'), key: 'errorType', width: 120 },
  { title: t('page.aiRetry'), key: 'retryCount', width: 90 },
  { title: t('page.aiCreatedAt'), dataIndex: 'createdAt', key: 'createdAt' },
  { title: t('page.aiUpdatedAt'), dataIndex: 'updatedAt', key: 'updatedAt' },
  { title: t('page.aiActions'), key: 'actions', width: 130 },
]

const creating = ref(false)
const createOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<AiTaskVo | null>(null)
const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<AiTaskVo>(getAiTaskPage, {
    buildParams: (query) => ({
      status: (query.status as string) || undefined,
      bizType: (query.bizType as string) || undefined,
      errorType: (query.errorType as string) || undefined,
    }),
  })
const createForm = reactive({
  bizType: 'text_summary',
  paramsText: '{"content":"Please enter the text to analyze","max_length":200}',
})
let taskStream: EventSource | null = null
// SSE 受控重连：指数退避 + 上限；终态或组件卸载后停止
let taskRetryCount = 0
let taskDisposed = false
let taskStopped = false
let taskReconnectTimer: number | undefined
const TASK_MAX_RETRY = 8

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
  closeTaskStream()
  taskStopped = false
  taskRetryCount = 0
  await loadDetail(record.id)
  if (detail.value && !isTerminal(detail.value.status)) {
    startTaskStream(record.id)
  }
}

async function startTaskStream(id: number) {
  if (taskStream || taskDisposed || taskStopped) {
    return
  }
  let ticket: string
  try {
    ticket = await getAiSseTicket()
  } catch {
    scheduleTaskReconnect(id)
    return
  }
  if (taskStream || taskDisposed || taskStopped) {
    return
  }
  const source = new EventSource(`${API_BASE_URL}/ai/tasks/${id}/stream?ticket=${encodeURIComponent(ticket)}`)
  source.addEventListener('task', (event) => {
    try {
      const data = JSON.parse((event as MessageEvent).data) as AiTaskVo
      detail.value = data
      taskRetryCount = 0
      if (isTerminal(data.status)) {
        stopTaskStream()
      }
    } catch {
      // ignore malformed events
    }
  })
  source.onerror = () => {
    source.close()
    taskStream = null
    scheduleTaskReconnect(id)
  }
  taskStream = source
}

function scheduleTaskReconnect(id: number) {
  if (taskDisposed || taskStopped || taskRetryCount >= TASK_MAX_RETRY) {
    return
  }
  taskRetryCount += 1
  const delay = Math.min(1000 * 2 ** (taskRetryCount - 1), 30000)
  taskReconnectTimer = window.setTimeout(() => startTaskStream(id), delay)
}

function stopTaskStream() {
  taskStopped = true
  closeTaskStream()
}

async function loadDetail(id: number) {
  const data = await getAiTaskDetail(id)
  detail.value = data
  if (isTerminal(data.status)) {
    closeTaskStream()
  }
}

function isTerminal(status: string) {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status)
}

function closeTaskStream() {
  if (taskReconnectTimer) {
    clearTimeout(taskReconnectTimer)
    taskReconnectTimer = undefined
  }
  if (taskStream) {
    taskStream.close()
    taskStream = null
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

onBeforeUnmount(() => {
  taskDisposed = true
  closeTaskStream()
})
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

