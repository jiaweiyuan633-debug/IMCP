<template>
  <a-card :title="t('page.aiConfigTitle')">
    <a-table :columns="columns" :data-source="configs" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <StatusTag :value="record.enabled" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a v-permission="'ai:config:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
        </template>
      </template>
    </a-table>

    <ModalForm
      v-model:open="modalOpen"
      :title="t('page.aiConfigEdit')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.aiConfigName')" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item :label="t('page.aiProvider')">
          <a-select v-model:value="form.provider" :options="providerOptions" />
        </a-form-item>
        <a-form-item :label="t('page.aiModel')">
          <a-input v-model:value="form.model" placeholder="gpt-4o-mini" />
        </a-form-item>
        <a-form-item :label="t('page.aiBaseUrl')" required>
          <a-input v-model:value="form.baseUrl" placeholder="http://localhost:8000" />
        </a-form-item>
        <a-form-item :label="t('page.aiApiKey')">
          <a-input-password v-model:value="form.apiKey" />
        </a-form-item>
        <a-form-item :label="t('page.aiTimeout')">
          <a-input-number v-model:value="form.timeoutSeconds" :min="5" :max="300" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('page.aiDailyLimit')">
          <a-input-number v-model:value="form.dailyLimit" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('page.aiEnabled')">
          <a-switch v-model:checked="enabled" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getAiConfigs, updateAiConfig } from '@/api/ai'
import type { AiConfigVo } from '@/api/ai'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const columns = [
  { title: t('page.aiConfigCode'), dataIndex: 'code', key: 'code' },
  { title: t('page.aiConfigName'), dataIndex: 'name', key: 'name' },
  { title: t('page.aiProvider'), dataIndex: 'provider', key: 'provider', width: 90 },
  { title: t('page.aiModel'), dataIndex: 'model', key: 'model', ellipsis: true },
  { title: t('page.aiBaseUrl'), dataIndex: 'baseUrl', key: 'baseUrl', ellipsis: true },
  { title: t('page.aiTimeout'), dataIndex: 'timeoutSeconds', key: 'timeoutSeconds', width: 100 },
  { title: t('page.aiDailyLimit'), dataIndex: 'dailyLimit', key: 'dailyLimit', width: 110 },
  { title: t('page.aiEnabled'), key: 'enabled', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 90 },
]

const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const configs = ref<AiConfigVo[]>([])
const editingId = ref<number | undefined>()
const enabled = ref(true)
const form = reactive({
  name: '',
  provider: 'openai',
  model: '',
  baseUrl: '',
  apiKey: '',
  timeoutSeconds: 60,
  dailyLimit: 1000,
})

const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Local', value: 'local' },
]

async function loadData() {
  loading.value = true
  try {
    configs.value = await getAiConfigs()
  } finally {
    loading.value = false
  }
}

function openEdit(record: AiConfigVo) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    provider: record.provider || 'openai',
    model: record.model || '',
    baseUrl: record.baseUrl,
    apiKey: record.apiKey || '',
    timeoutSeconds: record.timeoutSeconds,
    dailyLimit: record.dailyLimit || 1000,
  })
  enabled.value = record.enabled === 1
  modalOpen.value = true
}

async function onSubmit() {
  if (!editingId.value || !form.name || !form.baseUrl) {
    message.warning(t('page.aiConfigWarning'))
    return
  }
  saving.value = true
  try {
    await updateAiConfig(editingId.value, {
      name: form.name,
      provider: form.provider,
      model: form.model || undefined,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey || undefined,
      timeoutSeconds: form.timeoutSeconds,
      dailyLimit: form.dailyLimit,
      enabled: enabled.value ? 1 : 0,
    })
    message.success(t('page.aiConfigUpdated'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
