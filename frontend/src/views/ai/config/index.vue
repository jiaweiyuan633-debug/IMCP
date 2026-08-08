<template>
  <a-card title="AI 配置">
    <a-table :columns="columns" :data-source="configs" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <StatusTag :value="record.enabled" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a v-permission="'ai:config:edit'" @click="openEdit(record)">编辑</a>
        </template>
      </template>
    </a-table>

    <ModalForm
      v-model:open="modalOpen"
      title="编辑 AI 服务配置"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="服务名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="服务地址" required>
          <a-input v-model:value="form.baseUrl" placeholder="http://localhost:8000" />
        </a-form-item>
        <a-form-item label="调用密钥">
          <a-input-password v-model:value="form.apiKey" />
        </a-form-item>
        <a-form-item label="超时时间（秒）">
          <a-input-number v-model:value="form.timeoutSeconds" :min="5" :max="300" />
        </a-form-item>
        <a-form-item label="启用">
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

const columns = [
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '服务地址', dataIndex: 'baseUrl', key: 'baseUrl' },
  { title: '超时(秒)', dataIndex: 'timeoutSeconds', key: 'timeoutSeconds', width: 100 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '操作', key: 'actions', width: 90 },
]

const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const configs = ref<AiConfigVo[]>([])
const editingId = ref<number | undefined>()
const enabled = ref(true)
const form = reactive({
  name: '',
  baseUrl: '',
  apiKey: '',
  timeoutSeconds: 60,
})

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
    baseUrl: record.baseUrl,
    apiKey: record.apiKey || '',
    timeoutSeconds: record.timeoutSeconds,
  })
  enabled.value = record.enabled === 1
  modalOpen.value = true
}

async function onSubmit() {
  if (!editingId.value || !form.name || !form.baseUrl) {
    message.warning('请填写服务名称和地址')
    return
  }
  saving.value = true
  try {
    await updateAiConfig(editingId.value, {
      name: form.name,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey || undefined,
      timeoutSeconds: form.timeoutSeconds,
      enabled: enabled.value ? 1 : 0,
    })
    message.success('配置已更新')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>


