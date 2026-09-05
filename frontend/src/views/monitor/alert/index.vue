<template>
  <a-card :title="t('page.monitorAlertTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-space>
        <a-button v-permission="'monitor:alert:add'" type="primary" @click="openCreate">{{ t('page.monitorAlertAdd') }}</a-button>
        <a-button v-permission="'monitor:alert:run'" @click="onRunCheck">{{ t('page.monitorAlertCheck') }}</a-button>
      </a-space>
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
        <template v-if="column.key === 'metric'">
          <a-tag color="blue">{{ metricLabel(record.metric) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'operator'">
          {{ record.operator === 'lt' ? '<' : '>' }}
        </template>
        <template v-else-if="column.key === 'enabled'">
          <a-switch :checked="record.enabled === 1" @change="(checked: boolean) => toggleEnabled(record, checked)" />
        </template>
        <template v-else-if="column.key === 'severity'">
          <a-tag :color="severityColor(record.severity || 'WARNING')">{{ record.severity || 'WARNING' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'monitor:alert:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'monitor:alert:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.monitorAlertEdit') : t('page.monitorAlertAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.monitorAlertRuleName')" required>
          <a-input v-model:value="form.ruleName" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertMetric')" required>
          <a-select v-model:value="form.metric" :options="metricOptions" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertOperator')" required>
          <a-select v-model:value="form.operator" :options="operatorOptions" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertThreshold')" required>
          <a-input-number v-model:value="form.threshold" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('page.aiEnabled')">
          <a-switch v-model:checked="form.enabled" :checked-value="1" :un-checked-value="0" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertSeverity')">
          <a-select v-model:value="form.severity" :options="severityOptions" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertSilence')">
          <a-input-number v-model:value="form.silenceMinutes" :min="1" :max="1440" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertWebhook')">
          <a-input v-model:value="form.webhookUrl" placeholder="https://example.com/hook" />
        </a-form-item>
        <a-form-item :label="t('page.monitorAlertRemark')">
          <a-textarea v-model:value="form.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import {
  createAlertRule,
  deleteAlertRule,
  getAlertRulePage,
  runAlertRuleCheck,
  updateAlertRule,
} from '@/api/monitor'
import type { AlertRuleVo } from '@/api/monitor'
import type { SearchField } from '@/types'
import { useTableQuery } from '@/composables/useTableQuery'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.monitorAlertRuleName'), prop: 'ruleName', placeholder: `${t('common.inputPlaceholder')}${t('page.monitorAlertRuleName')}` },
  {
    label: t('page.monitorJobStatus'),
    prop: 'enabled',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.monitorAlertRuleName'), dataIndex: 'ruleName', key: 'ruleName' },
  { title: t('page.monitorAlertMetric'), key: 'metric', width: 140 },
  { title: t('page.monitorAlertOperator'), key: 'operator', width: 80 },
  { title: t('page.monitorAlertThreshold'), dataIndex: 'threshold', key: 'threshold', width: 100 },
  { title: t('page.monitorAlertSeverity'), key: 'severity', width: 100 },
  { title: t('page.monitorAlertSilence'), dataIndex: 'silenceMinutes', key: 'silenceMinutes', width: 90 },
  { title: t('page.monitorJobStatus'), key: 'enabled', width: 80 },
  { title: t('page.monitorAlertRemark'), dataIndex: 'remark', key: 'remark' },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const metricOptions = [
  { label: `${t('page.monitorCpu')} Usage`, value: 'CPU_USAGE' },
  { label: `${t('page.monitorMemory')} Usage`, value: 'MEMORY_USAGE' },
  { label: `${t('page.monitorJvm')} Usage`, value: 'JVM_USAGE' },
  { label: `${t('page.monitorDisk')} Usage`, value: 'DISK_USAGE' },
]

const operatorOptions = [
  { label: '>', value: 'gt' },
  { label: '<', value: 'lt' },
]

const severityOptions = [
  { label: 'INFO', value: 'INFO' },
  { label: 'WARNING', value: 'WARNING' },
  { label: 'CRITICAL', value: 'CRITICAL' },
]

function metricLabel(metric: string): string {
  return metricOptions.find((item) => item.value === metric)?.label || metric
}

function severityColor(severity: string): string {
  if (severity === 'CRITICAL') {
    return 'red'
  }
  if (severity === 'INFO') {
    return 'blue'
  }
  return 'orange'
}

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({
  ruleName: '',
  metric: 'CPU_USAGE',
  operator: 'gt',
  threshold: 80,
  enabled: 1,
  severity: 'WARNING',
  silenceMinutes: 10,
  webhookUrl: '',
  remark: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<AlertRuleVo>(getAlertRulePage, {
    buildParams: (query) => ({
      ruleName: (query.ruleName as string) || undefined,
      enabled: query.enabled as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    ruleName: '',
    metric: 'CPU_USAGE',
    operator: 'gt',
    threshold: 80,
    enabled: 1,
    severity: 'WARNING',
    silenceMinutes: 10,
    webhookUrl: '',
    remark: '',
  })
  modalOpen.value = true
}

function openEdit(record: AlertRuleVo) {
  editingId.value = record.id
  Object.assign(form, {
    ruleName: record.ruleName,
    metric: record.metric,
    operator: record.operator,
    threshold: record.threshold,
    enabled: record.enabled,
    severity: record.severity || 'WARNING',
    silenceMinutes: record.silenceMinutes || 10,
    webhookUrl: record.webhookUrl || '',
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.ruleName || !form.metric || !form.operator || form.threshold == null) {
    message.warning(`${t('common.inputPlaceholder')}${t('page.monitorAlertRuleName')}`)
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAlertRule({ ...form, id: editingId.value })
    } else {
      await createAlertRule(form)
    }
    message.success(t('page.monitorAlertSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(record: AlertRuleVo, checked: boolean) {
  await updateAlertRule({
    id: record.id,
    ruleName: record.ruleName,
    metric: record.metric,
    operator: record.operator,
    threshold: record.threshold,
    enabled: checked ? 1 : 0,
    severity: record.severity || 'WARNING',
    silenceMinutes: record.silenceMinutes || 10,
    webhookUrl: record.webhookUrl,
    remark: record.remark,
  })
  message.success(t('page.monitorJobStatusUpdated'))
  loadData()
}

function onDelete(record: AlertRuleVo) {
  Modal.confirm({
    title: t('page.monitorAlertDeleteTitle'),
    content: t('page.monitorAlertDeleteConfirm', { name: record.ruleName }),
    onOk: async () => {
      await deleteAlertRule(record.id)
      message.success(t('page.monitorAlertDeleted'))
      loadData()
    },
  })
}

async function onRunCheck() {
  const count = await runAlertRuleCheck()
  message.success(count > 0 ? t('page.monitorAlertRunSuccess', { count }) : t('page.monitorAlertRunNone'))
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
