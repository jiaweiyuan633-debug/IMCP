<template>
  <a-card :title="t('page.aiPromptTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'ai:prompt:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.aiPromptAdd') }}
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
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'ai:prompt:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'ai:prompt:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.aiPromptEdit') : t('page.aiPromptAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.aiPromptCode')" required>
          <a-input v-model:value="form.code" :maxlength="50" />
        </a-form-item>
        <a-form-item :label="t('page.aiPromptName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.aiPromptContent')" required>
          <a-textarea
            v-model:value="form.content"
            :rows="6"
            :placeholder="t('page.aiPromptContentPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('page.aiPromptVariables')">
          <a-input v-model:value="form.variables" placeholder="username, days" />
        </a-form-item>
        <a-form-item :label="t('page.aiPromptDescription')">
          <a-input v-model:value="form.description" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.aiPromptSort')">
          <a-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item :label="t('page.aiPromptStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { createPrompt, deletePrompt, getPromptPage, updatePrompt } from '@/api/aiEnhance'
import type { PromptSaveRequest, PromptVo } from '@/api/aiEnhance'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  {
    label: t('page.aiPromptName'),
    prop: 'name',
    placeholder: `${t('common.inputPlaceholder')}${t('page.aiPromptName')}`,
  },
  {
    label: t('page.aiPromptStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.aiPromptCode'), dataIndex: 'code', key: 'code', width: 160 },
  { title: t('page.aiPromptName'), dataIndex: 'name', key: 'name' },
  { title: t('page.aiPromptSort'), dataIndex: 'sort', key: 'sort', width: 80 },
  { title: t('page.aiPromptStatus'), dataIndex: 'status', key: 'status', width: 90 },
  { title: t('page.aiPromptDescription'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<PromptSaveRequest>({
  code: '',
  name: '',
  content: '',
  variables: '',
  description: '',
  sort: 0,
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<PromptVo>(getPromptPage, {
    buildParams: (query) => ({
      name: (query.name as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    code: '',
    name: '',
    content: '',
    variables: '',
    description: '',
    sort: 0,
    status: 1,
  })
  modalOpen.value = true
}

function openEdit(record: PromptVo) {
  editingId.value = record.id
  Object.assign(form, {
    code: record.code,
    name: record.name,
    content: record.content,
    variables: record.variables || '',
    description: record.description || '',
    sort: record.sort,
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.code || !form.name || !form.content) {
    message.warning(t('page.aiPromptRequired'))
    return
  }
  saving.value = true
  try {
    const payload: PromptSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updatePrompt(payload)
    } else {
      await createPrompt(payload)
    }
    message.success(t('page.aiPromptSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: PromptVo) {
  Modal.confirm({
    title: t('page.aiPromptDeleteTitle'),
    content: t('page.aiPromptDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deletePrompt(record.id)
      message.success(t('page.aiPromptDeleted'))
      loadData()
    },
  })
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
