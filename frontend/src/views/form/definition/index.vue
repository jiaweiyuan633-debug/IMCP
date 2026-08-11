<template>
  <a-card :title="t('page.formDefTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'form:definition:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.formDefAdd') }}
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
          <a-tag :color="record.status === 1 ? 'green' : 'orange'">
            {{ record.status === 1 ? t('page.formDefPublished') : t('page.formDefDraft') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-if="record.status === 0" v-permission="'form:definition:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-if="record.status === 0" v-permission="'form:definition:publish'" @click="onPublish(record)">{{ t('page.formDefPublish') }}</a>
            <a v-permission="'form:definition:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.formDefEdit') : t('page.formDefAdd')"
      :loading="saving"
      :width="680"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.formDefName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.formDefCode')" required>
          <a-input v-model:value="form.code" :maxlength="64" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.formDefDescription')">
          <a-input v-model:value="form.description" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.formDefSchema')" required>
          <a-textarea
            v-model:value="form.schemaJson"
            :rows="8"
            :placeholder="t('page.formDefSchemaPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('page.formDefLayout')">
          <a-textarea v-model:value="form.layoutJson" :rows="2" :placeholder='{"columns":2}' />
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
import { useTableQuery } from '@/composables/useTableQuery'
import {
  createFormDefinition,
  deleteFormDefinition,
  getFormDefinitionPage,
  publishFormDefinition,
  updateFormDefinition,
} from '@/api/formEngine'
import type { FormDefinitionSaveRequest, FormDefinitionVo } from '@/api/formEngine'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.formDefName'), prop: 'name', placeholder: `${t('common.inputPlaceholder')}${t('page.formDefName')}` },
  { label: t('page.formDefCode'), prop: 'code', placeholder: `${t('common.inputPlaceholder')}${t('page.formDefCode')}` },
  {
    label: t('page.formDefStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('page.formDefDraft'), value: 0 },
      { label: t('page.formDefPublished'), value: 1 },
    ],
  },
]

const columns = [
  { title: t('page.formDefName'), dataIndex: 'name', key: 'name' },
  { title: t('page.formDefCode'), dataIndex: 'code', key: 'code', width: 140 },
  { title: t('page.formDefDescription'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('page.formDefStatus'), dataIndex: 'status', key: 'status', width: 100 },
  { title: t('page.formDefCreatedAt'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('common.actions'), key: 'actions', width: 150 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<FormDefinitionSaveRequest>({
  name: '',
  code: '',
  description: '',
  schemaJson: '',
  layoutJson: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<FormDefinitionVo>(getFormDefinitionPage, {
    buildParams: (query) => ({
      name: (query.name as string) || undefined,
      code: (query.code as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    name: '',
    code: '',
    description: '',
    schemaJson: '',
    layoutJson: '',
    version: undefined,
  })
  modalOpen.value = true
}

function openEdit(record: FormDefinitionVo) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    code: record.code,
    description: record.description || '',
    schemaJson: record.schemaJson || '',
    layoutJson: record.layoutJson || '',
    version: record.version,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.name || !form.code || !form.schemaJson) {
    message.warning(t('page.formDefRequired'))
    return
  }
  try {
    const schema = JSON.parse(form.schemaJson)
    if (!Array.isArray(schema)) {
      throw new Error('schema must be array')
    }
  } catch {
    message.error(t('page.formDefSchemaInvalid'))
    return
  }
  if (form.layoutJson) {
    try {
      JSON.parse(form.layoutJson)
    } catch {
      message.error(t('page.formDefLayoutInvalid'))
      return
    }
  }
  saving.value = true
  try {
    const payload: FormDefinitionSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateFormDefinition(payload)
    } else {
      await createFormDefinition(payload)
    }
    message.success(t('page.formDefSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onPublish(record: FormDefinitionVo) {
  Modal.confirm({
    title: t('page.formDefPublishTitle'),
    content: t('page.formDefPublishConfirm', { name: record.name }),
    onOk: async () => {
      await publishFormDefinition(record.id)
      message.success(t('page.formDefPublished'))
      loadData()
    },
  })
}

function onDelete(record: FormDefinitionVo) {
  Modal.confirm({
    title: t('page.formDefDeleteTitle'),
    content: t('page.formDefDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteFormDefinition(record.id)
      message.success(t('page.formDefDeleted'))
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
