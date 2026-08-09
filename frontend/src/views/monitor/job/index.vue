<template>
  <a-card :title="t('page.monitorJobTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'monitor:job:add'" type="primary" @click="openCreate">{{ t('page.monitorAddJob') }}</a-button>
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
          <a-switch :checked="record.status === 1" @change="(checked: boolean) => toggleStatus(record, checked)" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'monitor:job:run'" @click="onRun(record)">{{ t('page.monitorRun') }}</a>
            <a v-permission="'monitor:job:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'monitor:job:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
            <a @click="openLogs(record)">{{ t('page.monitorLog') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.monitorEditJob') : t('page.monitorAddJob')"
      :loading="saving"
      width="560"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.monitorJobName')" required>
          <a-input v-model:value="form.jobName" />
        </a-form-item>
        <a-form-item :label="t('page.monitorJobGroup')" required>
          <a-input v-model:value="form.jobGroup" />
        </a-form-item>
        <a-form-item :label="t('page.monitorInvokeTarget')" required>
          <a-input v-model:value="form.invokeTarget" placeholder="demoTask.runDemo" />
        </a-form-item>
        <a-form-item :label="t('page.monitorCron')" required>
          <a-input v-model:value="form.cronExpression" placeholder="0/30 * * * * ?" />
        </a-form-item>
        <a-form-item :label="t('common.enabled')">
          <a-select v-model:value="form.concurrent" :options="yesNoOptions" />
        </a-form-item>
        <a-form-item :label="t('page.monitorJobStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertRemark')">
          <a-textarea v-model:value="form.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="logOpen" :title="t('page.monitorLog')" width="860" :footer="null">
      <a-table
        :columns="logColumns"
        :data-source="logRecords"
        :loading="logLoading"
        row-key="id"
        :pagination="{ current: logPageNum, pageSize: logPageSize, total: logTotal, showSizeChanger: true }"
        @change="onLogChange"
      />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import {
  changeJobStatus,
  createJob,
  deleteJob,
  getJobLogPage,
  getJobPage,
  runJob,
  updateJob,
} from '@/api/monitor'
import type { JobVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.monitorJobName'), prop: 'jobName', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorJobName')}` },
  {
    label: t('page.monitorJobStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.monitorJobName'), dataIndex: 'jobName', key: 'jobName' },
  { title: t('page.monitorJobGroup'), dataIndex: 'jobGroup', key: 'jobGroup' },
  { title: t('page.monitorInvokeTarget'), dataIndex: 'invokeTarget', key: 'invokeTarget' },
  { title: t('page.monitorCron'), dataIndex: 'cronExpression', key: 'cronExpression' },
  { title: t('page.monitorJobStatus'), key: 'status', width: 80 },
  { title: t('common.actions'), key: 'actions', width: 200 },
]

const logColumns = [
  { title: t('page.monitorJobName'), dataIndex: 'jobName', key: 'jobName' },
  { title: t('page.monitorInvokeTarget'), dataIndex: 'invokeTarget', key: 'invokeTarget' },
  { title: t('page.monitorStatus'), dataIndex: 'jobMessage', key: 'jobMessage' },
  { title: t('page.aiError'), dataIndex: 'exceptionInfo', key: 'exceptionInfo' },
  { title: 'Start', dataIndex: 'startTime', key: 'startTime' },
  { title: 'End', dataIndex: 'endTime', key: 'endTime' },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const yesNoOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const records = ref<JobVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({
  jobName: '',
  jobGroup: 'DEFAULT',
  invokeTarget: '',
  cronExpression: '',
  concurrent: 1,
  status: 0,
  remark: '',
})

const logOpen = ref(false)
const logLoading = ref(false)
const logRecords = ref<Record<string, unknown>[]>([])
const logPageNum = ref(1)
const logPageSize = ref(10)
const logTotal = ref(0)
const logJobName = ref('')

async function loadData() {
  loading.value = true
  try {
    const data = await getJobPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      jobName: (searchModel.jobName as string) || undefined,
      status: searchModel.status as number | undefined,
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
  editingId.value = undefined
  Object.assign(form, {
    jobName: '',
    jobGroup: 'DEFAULT',
    invokeTarget: '',
    cronExpression: '',
    concurrent: 1,
    status: 0,
    remark: '',
  })
  modalOpen.value = true
}

function openEdit(record: JobVo) {
  editingId.value = record.id
  Object.assign(form, {
    jobName: record.jobName,
    jobGroup: record.jobGroup,
    invokeTarget: record.invokeTarget,
    cronExpression: record.cronExpression,
    concurrent: record.concurrent,
    status: record.status,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.jobName || !form.invokeTarget || !form.cronExpression) {
    message.warning(t('page.monitorJobRequired'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateJob({ ...form, id: editingId.value })
    } else {
      await createJob(form)
    }
    message.success(t('page.monitorJobSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(record: JobVo, checked: boolean) {
  await changeJobStatus(record.id, checked ? 1 : 0)
  message.success(t('page.monitorJobStatusUpdated'))
  loadData()
}

function onRun(record: JobVo) {
  Modal.confirm({
    title: t('page.monitorJobRunTitle'),
    content: t('page.monitorJobRunConfirm', { name: record.jobName }),
    onOk: async () => {
      await runJob(record.id)
      message.success(t('page.monitorJobRunSuccess'))
    },
  })
}

function onDelete(record: JobVo) {
  Modal.confirm({
    title: t('page.monitorJobDeleteTitle'),
    content: t('page.monitorJobDeleteConfirm', { name: record.jobName }),
    onOk: async () => {
      await deleteJob(record.id)
      message.success(t('page.monitorJobDeleted'))
      loadData()
    },
  })
}

function openLogs(record: JobVo) {
  logJobName.value = record.jobName
  logPageNum.value = 1
  logOpen.value = true
  loadLogs()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await getJobLogPage({
      pageNum: logPageNum.value,
      pageSize: logPageSize.value,
      jobName: logJobName.value,
    })
    logRecords.value = data.records as Record<string, unknown>[]
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

function onLogChange(paginationValue: { current?: number; pageSize?: number }) {
  logPageNum.value = paginationValue.current || 1
  logPageSize.value = paginationValue.pageSize || 10
  loadLogs()
}

loadData()
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
