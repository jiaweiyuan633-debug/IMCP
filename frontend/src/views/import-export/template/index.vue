<template>
  <a-card :title="t('page.ieTemplateTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'importexport:template:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.ieTemplateAdd') }}
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
        <template v-if="column.key === 'type'">
          <a-tag :color="record.type === 'import' ? 'green' : 'blue'">
            {{ record.type === 'import' ? t('page.ieTemplateTypeImport') : t('page.ieTemplateTypeExport') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? t('common.enabled') : t('common.disabled') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'importexport:template:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'importexport:template:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.ieTemplateEdit') : t('page.ieTemplateAdd')"
      :loading="saving"
      :width="680"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.ieTemplateName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.ieTemplateCode')" required>
          <a-input v-model:value="form.code" :maxlength="64" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.ieTemplateType')" required>
          <a-select v-model:value="form.type" :options="typeOptions" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.ieTemplateEntityKey')" required>
          <a-input v-model:value="form.entityKey" :maxlength="64" :placeholder="t('page.ieTemplateEntityKeyPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.ieTemplateConfig')" required>
          <a-textarea
            v-model:value="form.configJson"
            :rows="8"
            placeholder='{"sheetName":"用户","columns":[{"key":"username","header":"用户名","required":true,"dataType":"string"}]}'
          />
        </a-form-item>
        <a-form-item :label="t('page.ieTemplateStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.ieTemplateRemark')">
          <a-textarea v-model:value="form.remark" :rows="2" :maxlength="255" />
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
  createImportTemplate,
  deleteImportTemplate,
  getImportTemplatePage,
  updateImportTemplate,
} from '@/api/importExport'
import type { ImportExportTemplateSaveRequest, ImportExportTemplateVo } from '@/api/importExport'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.ieTemplateName'), prop: 'name', placeholder: `${t('common.inputPlaceholder')}${t('page.ieTemplateName')}` },
  { label: t('page.ieTemplateCode'), prop: 'code', placeholder: `${t('common.inputPlaceholder')}${t('page.ieTemplateCode')}` },
  {
    label: t('page.ieTemplateType'),
    prop: 'type',
    type: 'select',
    options: [
      { label: t('page.ieTemplateTypeImport'), value: 'import' },
      { label: t('page.ieTemplateTypeExport'), value: 'export' },
    ],
  },
]

const columns = [
  { title: t('page.ieTemplateName'), dataIndex: 'name', key: 'name' },
  { title: t('page.ieTemplateCode'), dataIndex: 'code', key: 'code', width: 140 },
  { title: t('page.ieTemplateType'), dataIndex: 'type', key: 'type', width: 90 },
  { title: t('page.ieTemplateEntityKey'), dataIndex: 'entityKey', key: 'entityKey', width: 130 },
  { title: t('page.ieTemplateStatus'), dataIndex: 'status', key: 'status', width: 90 },
  dateColumn('createdAt', { title: t('page.ieTemplateCreatedAt'), width: 170 }),
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const typeOptions = [
  { label: t('page.ieTemplateTypeImport'), value: 'import' },
  { label: t('page.ieTemplateTypeExport'), value: 'export' },
]
const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<ImportExportTemplateSaveRequest>({
  name: '',
  code: '',
  type: 'import',
  entityKey: '',
  configJson: '',
  remark: '',
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ImportExportTemplateVo>(getImportTemplatePage, {
    buildParams: (query) => ({
      name: (query.name as string) || undefined,
      code: (query.code as string) || undefined,
      type: (query.type as string) || undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    name: '',
    code: '',
    type: 'import',
    entityKey: '',
    configJson: '',
    remark: '',
    status: 1,
    version: undefined,
  })
  modalOpen.value = true
}

function openEdit(record: ImportExportTemplateVo) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    code: record.code,
    type: record.type,
    entityKey: record.entityKey,
    configJson: record.configJson,
    remark: record.remark || '',
    status: record.status,
    version: record.version,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.name || !form.code || !form.type || !form.entityKey || !form.configJson) {
    message.warning(t('page.ieTemplateRequired'))
    return
  }
  try {
    JSON.parse(form.configJson)
  } catch {
    message.error(t('page.ieTemplateJsonInvalid'))
    return
  }
  saving.value = true
  try {
    const payload: ImportExportTemplateSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateImportTemplate(payload)
    } else {
      await createImportTemplate(payload)
    }
    message.success(t('page.ieTemplateSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: ImportExportTemplateVo) {
  Modal.confirm({
    title: t('page.ieTemplateDeleteTitle'),
    content: t('page.ieTemplateDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteImportTemplate(record.id)
      message.success(t('page.ieTemplateDeleted'))
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
