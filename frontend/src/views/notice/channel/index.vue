<template>
  <a-card :title="t('page.noticeChannelTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'notice:channel:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.noticeChannelAdd') }}
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
        <template v-if="column.key === 'channelType'">
          <a-tag :color="typeColor(record.channelType)">{{ typeLabel(record.channelType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-switch
            :checked="record.status === 1"
            :disabled="!canChangeStatus"
            @change="(checked: boolean) => onStatusChange(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'notice:channel:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'notice:channel:send'" @click="openSend(record)">{{ t('page.noticeChannelSend') }}</a>
            <a v-permission="'notice:channel:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.noticeChannelEdit') : t('page.noticeChannelAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.noticeChannelType')" required>
          <a-select
            v-model:value="form.channelType"
            :options="channelTypeOptions"
            :disabled="!!editingId"
            @change="onTypeChange"
          />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelName')" required>
          <a-input v-model:value="form.channelName" :maxlength="50" />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelConfig')" required>
          <a-button size="small" style="margin-bottom: 8px" @click="fillTemplate">
            {{ t('page.noticeChannelConfigTemplate') }}
          </a-button>
          <a-textarea v-model:value="form.configJson" :rows="6" :placeholder="configHint" />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelSort')">
          <a-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelDescription')">
          <a-input v-model:value="form.description" :maxlength="255" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <ModalForm
      v-model:open="sendOpen"
      :title="t('page.noticeChannelSendTitle')"
      :loading="sending"
      @ok="onSend"
    >
      <a-alert
        class="send-tip"
        :message="`${typeLabel(currentChannel?.channelType)} · ${currentChannel?.channelName}`"
        type="info"
        show-icon
      />
      <a-form layout="vertical" :model="sendForm">
        <a-form-item :label="t('page.noticeChannelTarget')" required>
          <a-input v-model:value="sendForm.target" :placeholder="targetPlaceholder" />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelTitleField')" required>
          <a-input v-model:value="sendForm.title" :maxlength="200" />
        </a-form-item>
        <a-form-item :label="t('page.noticeChannelContent')">
          <a-textarea v-model:value="sendForm.content" :rows="4" :maxlength="4000" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { usePermissionStore } from '@/stores/permission'
import {
  createChannel,
  deleteChannel,
  getChannelPage,
  sendChannelMessage,
  updateChannel,
  updateChannelStatus,
} from '@/api/channel'
import type { ChannelConfigSaveRequest, ChannelConfigVo } from '@/api/channel'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canChangeStatus = computed(() => permissionStore.perms.includes('notice:channel:status'))

const channelTypeOptions = [
  { label: t('page.noticeChannelTypeMail'), value: 'MAIL' },
  { label: t('page.noticeChannelTypeSms'), value: 'SMS' },
  { label: t('page.noticeChannelTypeDingtalk'), value: 'DINGTALK' },
  { label: t('page.noticeChannelTypeWecom'), value: 'WECOM' },
  { label: t('page.noticeChannelTypeWebhook'), value: 'WEBHOOK' },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const TYPE_COLORS: Record<string, string> = {
  MAIL: 'blue',
  SMS: 'orange',
  DINGTALK: 'cyan',
  WECOM: 'green',
  WEBHOOK: 'purple',
}

const TYPE_LABELS: Record<string, () => string> = {
  MAIL: () => t('page.noticeChannelTypeMail'),
  SMS: () => t('page.noticeChannelTypeSms'),
  DINGTALK: () => t('page.noticeChannelTypeDingtalk'),
  WECOM: () => t('page.noticeChannelTypeWecom'),
  WEBHOOK: () => t('page.noticeChannelTypeWebhook'),
}

const CONFIG_TEMPLATES: Record<string, string> = {
  MAIL: `{\n  "host": "smtp.example.com",\n  "port": 465,\n  "username": "user@example.com",\n  "password": "授权码",\n  "from": "user@example.com"\n}`,
  SMS: `{\n  "url": "https://sms-gateway.example.com/send",\n  "apiKey": "xxx",\n  "signName": "签名",\n  "templateId": "template_id"\n}`,
  DINGTALK: `{\n  "webhook": "https://oapi.dingtalk.com/robot/send?access_token=xxx",\n  "secret": ""\n}`,
  WECOM: `{\n  "webhook": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"\n}`,
  WEBHOOK: `{\n  "url": "https://example.com/hook",\n  "method": "POST",\n  "headers": {\n    "Authorization": "Bearer xxx"\n  }\n}`,
}

function typeLabel(type?: string): string {
  return type ? TYPE_LABELS[type]?.() ?? type : ''
}

function typeColor(type?: string): string {
  return type ? TYPE_COLORS[type] || 'default' : 'default'
}

function targetPlaceholder(): string {
  if (form.channelType === 'MAIL') {
    return 'user@example.com'
  }
  if (form.channelType === 'SMS') {
    return '13800000000'
  }
  return ''
}

const searchFields: SearchField[] = [
  {
    label: t('page.noticeChannelType'),
    prop: 'channelType',
    type: 'select',
    options: channelTypeOptions,
  },
  {
    label: t('page.noticeChannelStatus'),
    prop: 'status',
    type: 'select',
    options: statusOptions,
  },
]

const columns = [
  { title: t('page.noticeChannelName'), dataIndex: 'channelName', key: 'channelName' },
  { title: t('page.noticeChannelType'), dataIndex: 'channelType', key: 'channelType', width: 110 },
  { title: t('page.noticeChannelStatus'), dataIndex: 'status', key: 'status', width: 90 },
  { title: t('page.noticeChannelSort'), dataIndex: 'sort', key: 'sort', width: 70 },
  { title: t('page.noticeChannelDescription'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('common.actions'), key: 'actions', width: 150 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<ChannelConfigSaveRequest>({
  channelType: 'MAIL',
  channelName: '',
  configJson: CONFIG_TEMPLATES.MAIL,
  status: 1,
  sort: 0,
  description: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ChannelConfigVo>(getChannelPage, {
    buildParams: (query) => ({
      channelType: (query.channelType as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function configHint(): string {
  return `${t('page.noticeChannelConfigHint')} ${CONFIG_TEMPLATES[form.channelType] || ''}`
}

function fillTemplate() {
  form.configJson = CONFIG_TEMPLATES[form.channelType] || ''
}

function onTypeChange() {
  if (!form.configJson || !form.configJson.includes('"host"') && form.channelType === 'MAIL') {
    fillTemplate()
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    channelType: 'MAIL',
    channelName: '',
    configJson: CONFIG_TEMPLATES.MAIL,
    status: 1,
    sort: 0,
    description: '',
  })
  modalOpen.value = true
}

function openEdit(record: ChannelConfigVo) {
  editingId.value = record.id
  Object.assign(form, {
    channelType: record.channelType,
    channelName: record.channelName,
    configJson: record.configJson || CONFIG_TEMPLATES[record.channelType] || '',
    status: record.status,
    sort: record.sort,
    description: record.description || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.channelType || !form.channelName || !form.configJson) {
    message.warning(t('page.noticeChannelRequired'))
    return
  }
  saving.value = true
  try {
    const payload: ChannelConfigSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateChannel(payload)
    } else {
      await createChannel(payload)
    }
    message.success(t('page.noticeChannelSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onStatusChange(record: ChannelConfigVo, checked: boolean) {
  const next = checked ? 1 : 0
  const previous = record.status
  record.status = next
  try {
    await updateChannelStatus(record.id, next)
  } catch {
    record.status = previous
  }
}

function onDelete(record: ChannelConfigVo) {
  Modal.confirm({
    title: t('page.noticeChannelDelete'),
    content: t('page.noticeChannelDeleteConfirm', { name: record.channelName }),
    onOk: async () => {
      await deleteChannel(record.id)
      message.success(t('page.noticeChannelDeleted'))
      loadData()
    },
  })
}

const sendOpen = ref(false)
const sending = ref(false)
const currentChannel = ref<ChannelConfigVo>()
const sendForm = reactive({ target: '', title: '', content: '' })

function openSend(record: ChannelConfigVo) {
  currentChannel.value = record
  Object.assign(sendForm, { target: '', title: '', content: '' })
  sendOpen.value = true
}

async function onSend() {
  if (!currentChannel.value || !sendForm.target || !sendForm.title) {
    message.warning(t('page.noticeChannelSendNeedTarget'))
    return
  }
  sending.value = true
  try {
    await sendChannelMessage({
      channelId: currentChannel.value.id,
      target: sendForm.target,
      title: sendForm.title,
      content: sendForm.content,
    })
    message.success(t('page.noticeChannelSendSuccess'))
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
.send-tip {
  margin-bottom: 16px;
}
</style>
