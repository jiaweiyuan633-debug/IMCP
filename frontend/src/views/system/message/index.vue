<template>
  <a-card :title="t('page.messageTitle')">
    <a-tabs v-model:active-key="activeTab" @change="onTabChange">
      <a-tab-pane key="messages" :tab="t('page.messageMine')">
        <div class="toolbar">
          <ProSearchForm
            :fields="messageSearchFields"
            :loading="messageLoading"
            @search="onMessageSearch"
            @reset="onMessageReset"
          />
          <div class="toolbar-actions">
            <a-button @click="onMarkAllRead">{{ t('page.noticeMarkAllRead') }}</a-button>
            <a-button v-permission="'system:message:add'" type="primary" @click="openSend">
              {{ t('page.messageSend') }}
            </a-button>
          </div>
        </div>
        <ProTable
          v-model:page-num="messagePageNum"
          v-model:page-size="messagePageSize"
          :columns="messageColumns"
          :data-source="messageRecords"
          :loading="messageLoading"
          :total="messageTotal"
          row-key="id"
          @change="loadMessages"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'messageType'">
              <a-tag :color="record.messageType === 'TODO' ? 'orange' : 'blue'">
                {{ record.messageType === 'TODO' ? t('page.messageTypeTodo') : t('page.messageTypeSystem') }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'readFlag'">
              <a-tag :color="record.readFlag === 1 ? 'default' : 'green'">
                {{ record.readFlag === 1 ? t('page.messageRead') : t('page.messageUnread') }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="openDetail(record)">{{ t('page.messageView') }}</a>
                <a v-if="record.readFlag !== 1" @click="onMarkRead(record)">{{ t('page.messageReadTitle') }}</a>
              </a-space>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>

      <a-tab-pane key="todos" :tab="t('page.messageTodos')">
        <ProTable
          v-model:page-num="todoPageNum"
          v-model:page-size="todoPageSize"
          :columns="todoColumns"
          :data-source="todoRecords"
          :loading="todoLoading"
          :total="todoTotal"
          row-key="id"
          @change="loadTodos"
        >
          <template #bodyCell="{ column }">
            <template v-if="column.key === 'actions'">
              <a v-permission="'system:workflow:list'" @click="goWorkflow">{{ t('page.messageTodoGo') }}</a>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>

      <a-tab-pane key="notices" :tab="t('page.noticeTitle')">
        <ProTable
          v-model:page-num="noticePageNum"
          v-model:page-size="noticePageSize"
          :columns="noticeColumns"
          :data-source="noticeRecords"
          :loading="noticeLoading"
          :total="noticeTotal"
          row-key="id"
          @change="loadNotices"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'noticeType'">
              <a-tag :color="record.noticeType === 1 ? 'blue' : 'green'">
                {{ record.noticeType === 1 ? t('page.noticeNotice') : t('page.noticeAnnounce') }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a @click="openNotice(record)">{{ t('page.messageView') }}</a>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="detailOpen" :title="detailRecord?.title || ''" :footer="null" width="680">
      <div v-if="detailRecord" class="message-detail">
        <p>{{ detailRecord.content || '--' }}</p>
        <p class="message-meta">
          {{ formatTime(detailRecord.createdAt) }}
        </p>
      </div>
    </a-modal>

    <a-modal
      v-model:open="sendOpen"
      :title="t('page.messageSendTitle')"
      :confirm-loading="sendLoading"
      @ok="onSend"
    >
      <a-form :model="sendForm" layout="vertical">
        <a-form-item :label="t('page.messageTitleField')" required>
          <a-input v-model:value="sendForm.title" :placeholder="t('page.messageTitleField')" />
        </a-form-item>
        <a-form-item :label="t('page.messageContentField')">
          <a-textarea v-model:value="sendForm.content" :rows="4" />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="sendForm.broadcast">{{ t('page.messageBroadcast') }}</a-checkbox>
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import {
  getLatestNotices,
  getMessageDetail,
  getMessagePage,
  getMessageTodos,
  markAllMessageRead,
  markMessageRead,
  markNoticeRead,
  sendMessage,
  type MessageVo,
  type NoticeVo,
  type WorkflowVo,
} from '@/api/system'
import type { SearchField } from '@/types'
import dayjs from 'dayjs'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const activeTab = ref('messages')
const detailOpen = ref(false)
const detailRecord = ref<MessageVo | null>(null)

const messageSearchFields: SearchField[] = [
  {
    label: t('page.messageTypeSystem'),
    prop: 'messageType',
    type: 'select',
    options: [
      { label: t('page.messageTypeSystem'), value: 'SYSTEM' },
      { label: t('page.messageTypeTodo'), value: 'TODO' },
    ],
  },
  {
    label: t('page.messageRead'),
    prop: 'readStatus',
    type: 'select',
    options: [
      { label: t('page.messageUnread'), value: 0 },
      { label: t('page.messageRead'), value: 1 },
    ],
  },
]

const messageColumns = [
  { title: t('page.messageTitleField'), dataIndex: 'title', key: 'title', ellipsis: true },
  { title: t('page.messageTypeSystem'), key: 'messageType', width: 90 },
  { title: t('page.messageContentField'), dataIndex: 'content', key: 'content', ellipsis: true },
  { title: t('page.fileUploadTime'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('page.messageRead'), key: 'readFlag', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const todoColumns = [
  { title: t('page.workflowName'), dataIndex: 'processName', key: 'processName', ellipsis: true },
  { title: t('page.workflowCurrentNode'), dataIndex: 'currentNodeName', key: 'currentNodeName', width: 130 },
  { title: t('page.workflowApplicant'), dataIndex: 'applicantName', key: 'applicantName', width: 120 },
  { title: t('page.fileUploadTime'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('common.actions'), key: 'actions', width: 90 },
]

const noticeColumns = [
  { title: t('page.noticeTitleField'), dataIndex: 'noticeTitle', key: 'noticeTitle', ellipsis: true },
  { title: t('page.noticeType'), key: 'noticeType', width: 90 },
  { title: t('page.fileUploadTime'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('common.actions'), key: 'actions', width: 80 },
]

const messagePageNum = ref(1)
const messagePageSize = ref(10)
const messageTotal = ref(0)
const messageLoading = ref(false)
const messageRecords = ref<MessageVo[]>([])
const messageSearchModel = reactive<Record<string, unknown>>({})

const todoPageNum = ref(1)
const todoPageSize = ref(10)
const todoTotal = ref(0)
const todoLoading = ref(false)
const todoRecords = ref<WorkflowVo[]>([])

const noticePageNum = ref(1)
const noticePageSize = ref(10)
const noticeTotal = ref(0)
const noticeLoading = ref(false)
const noticeRecords = ref<NoticeVo[]>([])

const sendOpen = ref(false)
const sendLoading = ref(false)
const sendForm = reactive<{ title: string; content: string; broadcast: boolean }>({
  title: '',
  content: '',
  broadcast: true,
})

async function loadMessages() {
  messageLoading.value = true
  try {
    const data = await getMessagePage({
      pageNum: messagePageNum.value,
      pageSize: messagePageSize.value,
      messageType: messageSearchModel.messageType as string | undefined,
      readStatus: messageSearchModel.readStatus as number | undefined,
    })
    messageRecords.value = data.records
    messageTotal.value = data.total
  } finally {
    messageLoading.value = false
  }
}

async function loadTodos() {
  todoLoading.value = true
  try {
    const data = await getMessageTodos({
      pageNum: todoPageNum.value,
      pageSize: todoPageSize.value,
    })
    todoRecords.value = data.records
    todoTotal.value = data.total
  } finally {
    todoLoading.value = false
  }
}

async function loadNotices() {
  noticeLoading.value = true
  try {
    const records = await getLatestNotices(20)
    noticeRecords.value = records
    noticeTotal.value = records.length
  } finally {
    noticeLoading.value = false
  }
}

function onTabChange(key: string) {
  if (key === 'todos' && todoRecords.value.length === 0) {
    loadTodos()
  }
  if (key === 'notices' && noticeRecords.value.length === 0) {
    loadNotices()
  }
}

function onMessageSearch(model: Record<string, unknown>) {
  Object.assign(messageSearchModel, model)
  messagePageNum.value = 1
  loadMessages()
}

function onMessageReset() {
  Object.keys(messageSearchModel).forEach((key) => {
    messageSearchModel[key] = undefined
  })
  messagePageNum.value = 1
  loadMessages()
}

async function onMarkRead(record: MessageVo) {
  await markMessageRead(record.id)
  message.success(t('page.messageReadSuccess'))
  loadMessages()
}

async function onMarkAllRead() {
  await markAllMessageRead()
  message.success(t('page.messageAllReadSuccess'))
  loadMessages()
}

function openDetail(record: MessageVo) {
  detailRecord.value = record
  detailOpen.value = true
}

async function openNotice(record: NoticeVo) {
  await markNoticeRead(record.id)
  detailRecord.value = {
    id: record.id,
    messageType: 'SYSTEM',
    title: record.noticeTitle,
    content: record.noticeContent,
    readFlag: 1,
    createdAt: record.createdAt,
  }
  detailOpen.value = true
}

function openSend() {
  sendForm.title = ''
  sendForm.content = ''
  sendForm.broadcast = true
  sendOpen.value = true
}

async function onSend() {
  if (!sendForm.title.trim()) {
    message.error(t('page.messageTitleRequired'))
    return
  }
  sendLoading.value = true
  try {
    await sendMessage({
      title: sendForm.title,
      content: sendForm.content,
      receiverIds: sendForm.broadcast ? [] : undefined,
    })
    message.success(t('page.messageSendSuccess'))
    sendOpen.value = false
    loadMessages()
  } finally {
    sendLoading.value = false
  }
}

function goWorkflow() {
  router.push('/system/workflow')
}

function formatTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : ''
}

loadMessages()

async function openFromQuery() {
  const id = Number(route.query.id)
  if (!id) {
    return
  }
  try {
    const record = await getMessageDetail(id)
    openDetail(record)
  } catch {
    // ignore invalid message id
  }
}

openFromQuery()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex: none;
}

.message-detail {
  min-height: 120px;
}

.message-meta {
  margin-top: 24px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
</style>
