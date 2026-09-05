<template>
  <a-card :title="t('page.monitorJobTitle')">
    <div v-if="scheduler" class="scheduler-panel">
      <a-row :gutter="[16, 16]">
        <a-col :xs="12" :sm="8" :lg="6">
          <a-card class="metric-card">
            <div class="metric-value">
              <a-tag :color="scheduler.clustered ? 'success' : 'default'">
                {{ scheduler.clustered ? t('page.monitorSchedulerEnabled') : t('page.monitorSchedulerDisabled') }}
              </a-tag>
            </div>
            <div class="metric-label">{{ t('page.monitorSchedulerStatus') }}</div>
          </a-card>
        </a-col>
        <a-col :xs="12" :sm="8" :lg="6">
          <a-card class="metric-card">
            <div class="metric-value">{{ scheduler.nodeCount }}</div>
            <div class="metric-label">{{ t('page.monitorSchedulerNodeCount') }}</div>
          </a-card>
        </a-col>
        <a-col :xs="12" :sm="8" :lg="6">
          <a-card class="metric-card">
            <div class="metric-value">{{ scheduler.jobCount }}</div>
            <div class="metric-label">{{ t('page.monitorSchedulerJobCount') }}</div>
          </a-card>
        </a-col>
        <a-col :xs="12" :sm="8" :lg="6">
          <a-card class="metric-card">
            <div class="metric-value">{{ scheduler.triggerCount }}</div>
            <div class="metric-label">{{ t('page.monitorSchedulerTriggerCount') }}</div>
          </a-card>
        </a-col>
      </a-row>
      <a-descriptions :column="3" bordered size="small" class="scheduler-descriptions">
        <a-descriptions-item :label="t('page.monitorSchedulerInstanceId')">{{ scheduler.instanceId }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.monitorSchedulerInstanceName')">{{ scheduler.instanceName }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.monitorSchedulerThreadPool')">{{ scheduler.threadPoolSize }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.monitorSchedulerFiredCount')">
          <a-tag :color="scheduler.firedTriggerCount > 0 ? 'processing' : 'default'">{{ scheduler.firedTriggerCount }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item :label="t('page.monitorSchedulerPaused')">{{ scheduler.pausedTriggerCount }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.monitorSchedulerOverdue')">
          <a-tag :color="scheduler.overdueTriggerCount > 0 ? 'warning' : 'default'">{{ scheduler.overdueTriggerCount }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item :label="t('page.monitorSchedulerError')">
          <a-tag :color="scheduler.errorTriggerCount > 0 ? 'error' : 'default'">{{ scheduler.errorTriggerCount }}</a-tag>
        </a-descriptions-item>
      </a-descriptions>
    </div>
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
      :error="error"
      row-key="id"
      @change="loadData"
      @retry="loadData"
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
      <ProTable
        v-model:page-num="logPageNum"
        v-model:page-size="logPageSize"
        :columns="logColumns"
        :data-source="logRecords"
        :loading="logLoading"
        :total="logTotal"
        :error="logError"
        row-key="id"
        @change="loadLogs"
        @retry="loadLogs"
      />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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
  getSchedulerStatus,
  runJob,
  updateJob,
} from '@/api/monitor'
import type { JobLogVo, JobVo, SchedulerStatusVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'
import { useTableQuery } from '@/composables/useTableQuery'

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
  dateColumn('startTime', { title: t('page.monitorJobStartTime') }),
  dateColumn('endTime', { title: t('page.monitorJobEndTime') }),
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const yesNoOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const scheduler = ref<SchedulerStatusVo | null>(null)

async function loadSchedulerStatus() {
  try {
    scheduler.value = await getSchedulerStatus()
  } catch {
    // 集群状态查询失败时保留旧值，不影响任务列表主体功能
    scheduler.value = null
  }
}

onMounted(loadSchedulerStatus)

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({
  jobName: '',
  jobGroup: 'DEFAULT',
  invokeTarget: '',
  cronExpression: '',
  concurrent: 1,
  status: 0,
  remark: '',
})

const {
  pageNum,
  pageSize,
  total,
  loading,
  records,
  error,
  loadData,
  onSearch,
  onReset,
} = useTableQuery<JobVo>(getJobPage, {
  buildParams: (query) => ({
    jobName: (query.jobName as string) || undefined,
    status: query.status as number | undefined,
  }),
})

const logOpen = ref(false)
const logJobName = ref('')
const {
  pageNum: logPageNum,
  pageSize: logPageSize,
  total: logTotal,
  loading: logLoading,
  records: logRecords,
  error: logError,
  loadData: loadLogs,
} = useTableQuery<JobLogVo>(getJobLogPage, {
  immediate: false,
  buildParams: () => ({ jobName: logJobName.value || undefined }),
})

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
    loadSchedulerStatus()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(record: JobVo, checked: boolean) {
  await changeJobStatus(record.id, checked ? 1 : 0)
  message.success(t('page.monitorJobStatusUpdated'))
  loadData()
  loadSchedulerStatus()
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
      loadSchedulerStatus()
    },
  })
}

function openLogs(record: JobVo) {
  logJobName.value = record.jobName
  logPageNum.value = 1
  logOpen.value = true
  loadLogs()
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.scheduler-panel {
  margin-bottom: 16px;
}

.metric-card {
  text-align: center;
}

.metric-value {
  font-size: 26px;
  font-weight: 600;
}

.metric-label {
  color: #888;
  margin-top: 4px;
}

.scheduler-descriptions {
  margin-top: 16px;
}
</style>
