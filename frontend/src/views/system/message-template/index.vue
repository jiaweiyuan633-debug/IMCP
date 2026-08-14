<template>
  <a-card :title="t('page.messageTemplateTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'notice:message-template:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.messageTemplateAdd') }}
      </a-button>
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
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? t('common.enabled') : t('common.disabled') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'notice:message-template:send'" @click="openSend(record)">{{ t('page.messageTemplateSend') }}</a>
            <a v-permission="'notice:message-template:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-if="record.status === 1" v-permission="'notice:message-template:edit'" @click="onToggleStatus(record, 0)">{{ t('page.messageTemplateDisable') }}</a>
            <a v-else v-permission="'notice:message-template:edit'" @click="onToggleStatus(record, 1)">{{ t('page.messageTemplateEnable') }}</a>
            <a v-permission="'notice:message-template:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.messageTemplateEdit') : t('page.messageTemplateAdd')"
      :loading="saving"
      :width="640"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.messageTemplateCode')" required>
          <a-input v-model:value="form.templateCode" :maxlength="64" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateName')" required>
          <a-input v-model:value="form.templateName" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateType')">
          <a-input v-model:value="form.messageType" :maxlength="32" :placeholder="t('page.messageTemplateTypePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateTitleTemplate')" required>
          <a-input v-model:value="form.titleTemplate" :maxlength="200" :placeholder="t('page.messageTemplatePlaceholderHint')" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateContent')" required>
          <a-textarea v-model:value="form.contentTemplate" :rows="4" :maxlength="2000" :placeholder="t('page.messageTemplatePlaceholderHint')" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateContentType')">
          <a-select v-model:value="form.contentType" :options="contentTypeOptions" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateRemark')">
          <a-textarea v-model:value="form.remark" :rows="2" :maxlength="255" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="sendOpen" :title="t('page.messageTemplateSendTitle')" :confirm-loading="sending" @ok="onSend">
      <a-form layout="vertical">
        <a-form-item :label="t('page.messageTemplateCode')">
          <a-input :value="sendForm.templateCode" disabled />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateParams')">
          <a-textarea v-model:value="sendForm.paramsJson" :rows="4" :placeholder="t('page.messageTemplateParamsPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.messageTemplateReceivers')">
          <a-input v-model:value="sendForm.receiverIdsText" :placeholder="t('page.messageTemplateReceiversPlaceholder')" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import {
  createMessageTemplate,
  deleteMessageTemplate,
  getMessageTemplatePage,
  sendMessageTemplate,
  updateMessageTemplate,
  updateMessageTemplateStatus,
} from '@/api/messageTemplate'
import type { MessageTemplateSaveRequest, MessageTemplateVo } from '@/api/messageTemplate'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.messageTemplateCode'), prop: 'templateCode', placeholder: `${t('common.inputPlaceholder')}${t('page.messageTemplateCode')}` },
  { label: t('page.messageTemplateName'), prop: 'templateName', placeholder: `${t('common.inputPlaceholder')}${t('page.messageTemplateName')}` },
  { label: t('page.messageTemplateType'), prop: 'messageType', placeholder: `${t('common.inputPlaceholder')}${t('page.messageTemplateType')}` },
  {
    label: t('page.messageTemplateStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.messageTemplateCode'), dataIndex: 'templateCode', key: 'templateCode', width: 140 },
  { title: t('page.messageTemplateName'), dataIndex: 'templateName', key: 'templateName' },
  { title: t('page.messageTemplateType'), dataIndex: 'messageType', key: 'messageType', width: 110 },
  { title: t('page.messageTemplateContentType'), dataIndex: 'contentType', key: 'contentType', width: 90 },
  { title: t('page.messageTemplateStatus'), dataIndex: 'status', key: 'status', width: 90 },
  dateColumn('createdAt', { title: t('page.messageTemplateCreatedAt'), width: 170 }),
  { title: t('common.actions'), key: 'actions', width: 200 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]
const contentTypeOptions = [
  { label: 'TEXT', value: 'TEXT' },
  { label: 'HTML', value: 'HTML' },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<MessageTemplateSaveRequest>({
  templateCode: '',
  templateName: '',
  messageType: 'SYSTEM',
  titleTemplate: '',
  contentTemplate: '',
  contentType: 'TEXT',
  status: 1,
  remark: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<MessageTemplateVo>(getMessageTemplatePage, {
    buildParams: (query) => ({
      templateCode: (query.templateCode as string) || undefined,
      templateName: (query.templateName as string) || undefined,
      messageType: (query.messageType as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    templateCode: '',
    templateName: '',
    messageType: 'SYSTEM',
    titleTemplate: '',
    contentTemplate: '',
    contentType: 'TEXT',
    status: 1,
    remark: '',
  })
  modalOpen.value = true
}

function openEdit(record: MessageTemplateVo) {
  editingId.value = record.id
  Object.assign(form, {
    templateCode: record.templateCode,
    templateName: record.templateName,
    messageType: record.messageType || 'SYSTEM',
    titleTemplate: record.titleTemplate,
    contentTemplate: record.contentTemplate,
    contentType: record.contentType || 'TEXT',
    status: record.status,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.templateCode || !form.templateName || !form.titleTemplate || !form.contentTemplate) {
    message.warning(t('page.messageTemplateRequired'))
    return
  }
  saving.value = true
  try {
    const payload: MessageTemplateSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateMessageTemplate(payload)
    } else {
      await createMessageTemplate(payload)
    }
    message.success(t('page.messageTemplateSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onToggleStatus(record: MessageTemplateVo, status: number) {
  await updateMessageTemplateStatus(record.id, status)
  message.success(t('page.messageTemplateStatusUpdated'))
  loadData()
}

function onDelete(record: MessageTemplateVo) {
  Modal.confirm({
    title: t('page.messageTemplateDeleteTitle'),
    content: t('page.messageTemplateDeleteConfirm', { name: record.templateName }),
    onOk: async () => {
      await deleteMessageTemplate(record.id)
      message.success(t('page.messageTemplateDeleted'))
      loadData()
    },
  })
}

const sendOpen = ref(false)
const sending = ref(false)
const sendForm = reactive<{ templateCode: string; paramsJson: string; receiverIdsText: string }>({
  templateCode: '',
  paramsJson: '',
  receiverIdsText: '',
})

function openSend(record: MessageTemplateVo) {
  sendForm.templateCode = record.templateCode
  sendForm.paramsJson = ''
  sendForm.receiverIdsText = ''
  sendOpen.value = true
}

async function onSend() {
  let params: Record<string, unknown> | undefined
  if (sendForm.paramsJson.trim()) {
    try {
      params = JSON.parse(sendForm.paramsJson)
    } catch {
      message.error(t('page.messageTemplateParamsInvalid'))
      return
    }
  }
  const receiverIds = sendForm.receiverIdsText
    ? sendForm.receiverIdsText.split(/[,，\s]+/).map((s) => Number(s)).filter((n) => Number.isFinite(n))
    : undefined
  sending.value = true
  try {
    await sendMessageTemplate({
      templateCode: sendForm.templateCode,
      params,
      receiverIds,
    })
    message.success(t('page.messageTemplateSent'))
    sendOpen.value = false
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.danger {
  color: #ff4d4f;
}
</style>
