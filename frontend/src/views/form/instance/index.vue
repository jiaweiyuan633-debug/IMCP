<template>
  <a-card :title="t('page.formInstTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button @click="loadData">
        <ReloadOutlined />
        {{ t('common.refresh') }}
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
          <a-tag :color="statusMeta(record.status).color">{{ statusMeta(record.status).text }}</a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'form:instance:view'" @click="openView(record)">{{ t('page.formInstView') }}</a>
            <template v-if="record.status === 'SUBMITTED'">
              <a v-permission="'form:instance:approve'" @click="onApprove(record, 'APPROVED')">{{ t('page.formInstApprove') }}</a>
              <a v-permission="'form:instance:approve'" class="danger" @click="onApprove(record, 'REJECTED')">{{ t('page.formInstReject') }}</a>
            </template>
          </a-space>
        </template>
      </template>
    </ProTable>

    <a-modal v-model:open="viewOpen" :title="t('page.formInstViewTitle')" :footer="null">
      <a-descriptions :column="1" size="small" bordered>
        <a-descriptions-item :label="t('page.formInstId')">{{ current?.id }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.formInstFormCode')">{{ current?.formCode }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.formInstStatus')">
          <a-tag v-if="current" :color="statusMeta(current.status).color">{{ statusMeta(current.status).text }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item :label="t('page.formInstSubmitter')">{{ current?.submitterId ?? '—' }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.formInstSubmittedAt')">{{ formatDateTime(current?.submittedAt) }}</a-descriptions-item>
        <a-descriptions-item :label="t('page.formInstRemark')">{{ current?.remark || '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-divider style="margin: 12px 0" />
      <div class="data-label">{{ t('page.formInstData') }}</div>
      <a-input type="textarea" :value="dataJson" :auto-size="{ minRows: 3, maxRows: 12 }" readonly />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { approveFormInstance, getFormInstancePage } from '@/api/formEngine'
import type { FormInstanceVo } from '@/api/formEngine'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn, formatDateTime } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.formInstFormCode'), prop: 'formCode', placeholder: `${t('common.inputPlaceholder')}${t('page.formInstFormCode')}` },
  {
    label: t('page.formInstStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('page.formInstStatusSubmitted'), value: 'SUBMITTED' },
      { label: t('page.formInstStatusApproved'), value: 'APPROVED' },
      { label: t('page.formInstStatusRejected'), value: 'REJECTED' },
    ],
  },
]

const columns = [
  { title: t('page.formInstId'), dataIndex: 'id', key: 'id', width: 90 },
  { title: t('page.formInstFormCode'), dataIndex: 'formCode', key: 'formCode', width: 150 },
  { title: t('page.formInstStatus'), dataIndex: 'status', key: 'status', width: 110 },
  { title: t('page.formInstSubmitter'), dataIndex: 'submitterId', key: 'submitterId', width: 110 },
  dateColumn('submittedAt', { title: t('page.formInstSubmittedAt'), width: 180 }),
  { title: t('page.formInstRemark'), dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: t('common.actions'), key: 'actions', width: 180 },
]

function statusMeta(status?: string): { text: string; color: string } {
  if (status === 'APPROVED') {
    return { text: t('page.formInstStatusApproved'), color: 'green' }
  }
  if (status === 'REJECTED') {
    return { text: t('page.formInstStatusRejected'), color: 'red' }
  }
  return { text: t('page.formInstStatusSubmitted'), color: 'blue' }
}

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<FormInstanceVo>(getFormInstancePage, {
    buildParams: (query) => ({
      formCode: (query.formCode as string) || undefined,
      status: (query.status as string) || undefined,
    }),
  })

const viewOpen = ref(false)
const current = ref<FormInstanceVo | null>(null)

const dataJson = computed(() => (current.value ? JSON.stringify(current.value.data, null, 2) : ''))

function openView(record: FormInstanceVo) {
  current.value = record
  viewOpen.value = true
}

function onApprove(record: FormInstanceVo, status: string) {
  const approved = status === 'APPROVED'
  Modal.confirm({
    title: approved ? t('page.formInstApproveTitle') : t('page.formInstRejectTitle'),
    content: t('page.formInstApproveConfirm', { id: record.id }),
    onOk: async () => {
      await approveFormInstance(record.id, status)
      message.success(t('page.formInstStatusUpdated'))
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
.data-label {
  margin-bottom: 8px;
  font-weight: 500;
}
</style>
