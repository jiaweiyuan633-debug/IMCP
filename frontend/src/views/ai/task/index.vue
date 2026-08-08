<template>
  <a-card title="AI 任务">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'ai:task:create'" type="primary" @click="openCreate">创建任务</a-button>
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
            <a @click="openDetail(record)">详情</a>
            <a v-permission="'ai:task:cancel'" @click="onCancel(record)">取消</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="createOpen"
      title="创建 AI 任务"
      :loading="creating"
      @ok="onCreate"
    >
      <a-form layout="vertical" :model="createForm">
        <a-form-item label="业务类型" required>
          <a-select v-model:value="createForm.bizType" :options="bizTypeOptions" />
        </a-form-item>
        <a-form-item label="任务参数（JSON）" required>
          <a-textarea v-model:value="createForm.paramsText" :rows="6" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="detailOpen" title="任务详情" width="720" :footer="null">
      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="任务编号" :span="2">{{ detail?.taskNo }}</a-descriptions-item>
        <a-descriptions-item label="业务类型">{{ detail?.bizType }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <StatusTag v-if="detail" :value="detail.status" />
        </a-descriptions-item>
        <a-descriptions-item label="重试次数">{{ detail ? `${detail.retryCount} / ${detail.maxRetry}` : '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detail?.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="错误信息" :span="2">{{ detail?.errorMsg || '-' }}</a-descriptions-item>
      </a-descriptions>
      <a-divider />
      <div class="result-title">任务结果</div>
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

const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '排队中', value: 'QUEUED' },
  { label: '执行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
]

const bizTypeOptions = [
  { label: '文本摘要', value: 'text_summary' },
  { label: '关键词抽取', value: 'keyword_extract' },
]

const searchFields: SearchField[] = [
  { label: '业务类型', prop: 'bizType', type: 'select', options: bizTypeOptions },
  { label: '状态', prop: 'status', type: 'select', options: statusOptions },
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '任务编号', dataIndex: 'taskNo', key: 'taskNo' },
  { title: '业务类型', dataIndex: 'bizType', key: 'bizType' },
  { title: '状态', key: 'status', width: 100 },
  { title: '重试', key: 'retryCount', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt' },
  { title: '操作', key: 'actions', width: 130 },
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
  paramsText: '{"content":"请输入需要分析的文本","max_length":200}',
})
let pollTimer: ReturnType<typeof setInterval> | null = null

const prettyResult = computed(() => {
  const raw = detail.value?.result?.resultJson
  if (!raw) {
    return '暂无结果'
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
  createForm.paramsText = '{"content":"请输入需要分析的文本","max_length":200}'
  createOpen.value = true
}

async function onCreate() {
  let params: Record<string, unknown>
  try {
    params = JSON.parse(createForm.paramsText)
  } catch {
    message.error('任务参数不是合法 JSON')
    return
  }
  creating.value = true
  try {
    await createAiTask({ bizType: createForm.bizType, params })
    message.success('任务已创建')
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
    title: '确认取消任务',
    content: `确定取消任务 ${record.taskNo} 吗？`,
    onOk: async () => {
      await cancelAiTask(record.id)
      message.success('任务已取消')
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

