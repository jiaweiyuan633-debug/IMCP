<template>
  <a-card :title="t('page.tenantTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:tenant:add'" type="primary" @click="openCreate">{{ t('page.tenantAdd') }}</a-button>
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
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:tenant:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:tenant:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>
    <ModalForm v-model:open="modalOpen" :title="editingId ? t('page.tenantEdit') : t('page.tenantAdd')" :loading="saving" @ok="onSubmit">
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.tenantName')" required><a-input v-model:value="form.tenantName" /></a-form-item>
        <a-form-item :label="t('page.tenantCode')" required><a-input v-model:value="form.tenantCode" /></a-form-item>
        <a-form-item :label="t('page.tenantContact')"><a-input v-model:value="form.contactName" /></a-form-item>
        <a-form-item :label="t('page.tenantPhone')"><a-input v-model:value="form.contactPhone" /></a-form-item>
        <a-form-item :label="t('page.tenantStatus')"><a-select v-model:value="form.status" :options="statusOptions" /></a-form-item>
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
import StatusTag from '@/components/StatusTag.vue'
import { createTenant, deleteTenant, getTenantPage, updateTenant } from '@/api/system'
import type { TenantVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [{ label: t('page.tenantName'), prop: 'tenantName', placeholder: `${t('common.inputPlaceholder')}${t('page.tenantName')}` }]
const columns = [
  { title: t('page.tenantName'), dataIndex: 'tenantName', key: 'tenantName' },
  { title: t('page.tenantCode'), dataIndex: 'tenantCode', key: 'tenantCode' },
  { title: t('page.tenantContact'), dataIndex: 'contactName', key: 'contactName' },
  { title: t('page.tenantPhone'), dataIndex: 'contactPhone', key: 'contactPhone' },
  { title: t('page.tenantStatus'), key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 130 },
]
const statusOptions = [
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
const records = ref<TenantVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({ tenantName: '', tenantCode: '', contactName: '', contactPhone: '', status: 1 })

async function loadData() {
  loading.value = true
  try {
    const data = await getTenantPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      tenantName: (searchModel.tenantName as string) || undefined,
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
  Object.keys(searchModel).forEach((k) => (searchModel[k] = undefined))
  pageNum.value = 1
  loadData()
}
function openCreate() {
  editingId.value = undefined
  Object.assign(form, { tenantName: '', tenantCode: '', contactName: '', contactPhone: '', status: 1 })
  modalOpen.value = true
}
function openEdit(record: TenantVo) {
  editingId.value = record.id
  Object.assign(form, {
    tenantName: record.tenantName,
    tenantCode: record.tenantCode,
    contactName: record.contactName || '',
    contactPhone: record.contactPhone || '',
    status: record.status,
  })
  modalOpen.value = true
}
async function onSubmit() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateTenant({ ...form, id: editingId.value })
    } else {
      await createTenant(form)
    }
    message.success(t('page.tenantSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}
function onDelete(record: TenantVo) {
  Modal.confirm({
    title: t('page.tenantDeleteTitle'),
    content: t('page.tenantDeleteConfirm', { name: record.tenantName }),
    onOk: async () => {
      await deleteTenant(record.id)
      message.success(t('page.tenantDeleted'))
      loadData()
    },
  })
}
loadData()
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

