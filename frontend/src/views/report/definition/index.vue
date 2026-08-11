<template>
  <a-card :title="t('page.reportDefTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'report:definition:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.reportDefAdd') }}
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
            <a v-permission="'report:definition:execute'" @click="openExecute(record)">{{ t('page.reportDefExecute') }}</a>
            <a v-permission="'report:definition:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'report:definition:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.reportDefEdit') : t('page.reportDefAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.reportDefName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.reportDefCode')" required>
          <a-input v-model:value="form.code" :maxlength="64" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.reportDefCategory')">
          <a-input v-model:value="form.category" :maxlength="64" />
        </a-form-item>
        <a-form-item :label="t('page.reportDefDataSource')" required>
          <a-textarea v-model:value="form.dataSource" :rows="6" placeholder="SELECT ... FROM ..." />
        </a-form-item>
        <a-form-item :label="t('page.reportDefChartType')">
          <a-input v-model:value="form.chartType" :maxlength="32" />
        </a-form-item>
        <a-form-item :label="t('page.reportDefParamsJson')">
          <a-textarea v-model:value="form.paramsJson" :rows="2" placeholder='{"param1": "默认值"}' />
        </a-form-item>
        <a-form-item :label="t('page.reportDefStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.reportDefRemark')">
          <a-textarea v-model:value="form.remark" :rows="2" :maxlength="255" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="executeOpen" :title="t('page.reportDefExecuteTitle')" :footer="null" width="860">
      <a-alert
        v-if="!executeLoading && executeRows.length === 0"
        type="info"
        :message="t('page.reportDefEmpty')"
        style="margin-bottom: 12px"
      />
      <a-table :columns="executeColumns" :data-source="executeRows" size="small" :pagination="false" />
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
  createReportDefinition,
  deleteReportDefinition,
  executeReportDefinition,
  getReportDefinitionPage,
  updateReportDefinition,
} from '@/api/reportDefinition'
import type { ReportDefinitionSaveRequest, ReportDefinitionVo } from '@/api/reportDefinition'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.reportDefName'), prop: 'name', placeholder: `${t('common.inputPlaceholder')}${t('page.reportDefName')}` },
  { label: t('page.reportDefCode'), prop: 'code', placeholder: `${t('common.inputPlaceholder')}${t('page.reportDefCode')}` },
  { label: t('page.reportDefCategory'), prop: 'category', placeholder: `${t('common.inputPlaceholder')}${t('page.reportDefCategory')}` },
]

const columns = [
  { title: t('page.reportDefName'), dataIndex: 'name', key: 'name' },
  { title: t('page.reportDefCode'), dataIndex: 'code', key: 'code', width: 140 },
  { title: t('page.reportDefCategory'), dataIndex: 'category', key: 'category', width: 120 },
  { title: t('page.reportDefChartType'), dataIndex: 'chartType', key: 'chartType', width: 110 },
  { title: t('page.reportDefStatus'), dataIndex: 'status', key: 'status', width: 90 },
  { title: t('page.reportDefCreatedAt'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('common.actions'), key: 'actions', width: 160 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<ReportDefinitionSaveRequest>({
  name: '',
  code: '',
  category: '',
  dataSource: '',
  chartType: '',
  paramsJson: '',
  remark: '',
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ReportDefinitionVo>(getReportDefinitionPage, {
    buildParams: (query) => ({
      name: (query.name as string) || undefined,
      code: (query.code as string) || undefined,
      category: (query.category as string) || undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    name: '',
    code: '',
    category: '',
    dataSource: '',
    chartType: '',
    paramsJson: '',
    remark: '',
    status: 1,
    version: undefined,
  })
  modalOpen.value = true
}

function openEdit(record: ReportDefinitionVo) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    code: record.code,
    category: record.category || '',
    dataSource: record.dataSource,
    chartType: record.chartType || '',
    paramsJson: record.paramsJson || '',
    remark: record.remark || '',
    status: record.status,
    version: record.version,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.name || !form.code || !form.dataSource) {
    message.warning(t('page.reportDefRequired'))
    return
  }
  saving.value = true
  try {
    const payload: ReportDefinitionSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateReportDefinition(payload)
    } else {
      await createReportDefinition(payload)
    }
    message.success(t('page.reportDefSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: ReportDefinitionVo) {
  Modal.confirm({
    title: t('page.reportDefDeleteTitle'),
    content: t('page.reportDefDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteReportDefinition(record.id)
      message.success(t('page.reportDefDeleted'))
      loadData()
    },
  })
}

const executeOpen = ref(false)
const executeLoading = ref(false)
const executeColumns = ref<{ title: string; dataIndex: string; key: string }[]>([])
const executeRows = ref<Record<string, unknown>[]>([])

async function openExecute(record: ReportDefinitionVo) {
  executeColumns.value = []
  executeRows.value = []
  executeOpen.value = true
  executeLoading.value = true
  try {
    const result = await executeReportDefinition(record.id, {})
    executeColumns.value = result.columns.map((name) => ({ title: name, dataIndex: name, key: name }))
    executeRows.value = result.rows
  } finally {
    executeLoading.value = false
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
