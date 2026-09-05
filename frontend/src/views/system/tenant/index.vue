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
        <a-form-item :label="t('page.tenantUserLimit')"><a-input-number v-model:value="form.userLimit" :min="1" style="width: 100%" /></a-form-item>
        <a-form-item :label="t('page.tenantStorageLimit')"><a-input-number v-model:value="form.storageLimitMb" :min="0" style="width: 100%" /></a-form-item>
        <a-form-item :label="t('page.tenantAdmin')">
          <a-select
            v-model:value="form.adminUserId"
            :options="userOptions"
            allow-clear
            show-search
            option-filter-prop="label"
            :placeholder="t('common.selectPlaceholder')"
          />
        </a-form-item>
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
import {
  createTenant,
  deleteTenant,
  getTenantAdminCandidates,
  getTenantPage,
  getTenantUsers,
  updateTenant,
} from '@/api/system'
import type { TenantAdminCandidateVo, TenantVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { useTableQuery } from '@/composables/useTableQuery'

const { t } = useI18n()

const searchFields: SearchField[] = [{ label: t('page.tenantName'), prop: 'tenantName', placeholder: `${t('common.inputPlaceholder')}${t('page.tenantName')}` }]
const columns = [
  { title: t('page.tenantName'), dataIndex: 'tenantName', key: 'tenantName' },
  { title: t('page.tenantCode'), dataIndex: 'tenantCode', key: 'tenantCode' },
  { title: t('page.tenantContact'), dataIndex: 'contactName', key: 'contactName' },
  { title: t('page.tenantPhone'), dataIndex: 'contactPhone', key: 'contactPhone' },
  { title: t('page.tenantUserLimit'), dataIndex: 'userLimit', key: 'userLimit', width: 90 },
  { title: t('page.tenantStorageLimit'), dataIndex: 'storageLimitMb', key: 'storageLimitMb', width: 110 },
  { title: t('page.tenantStatus'), key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 130 },
]
const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({
  tenantName: '',
  tenantCode: '',
  contactName: '',
  contactPhone: '',
  userLimit: 100,
  storageLimitMb: 1024,
  adminUserId: undefined as number | undefined,
  status: 1,
})
const userOptions = ref<{ label: string; value: number }[]>([])

function toUserOptions(candidates: TenantAdminCandidateVo[], showTenant: boolean) {
  return candidates.map((user) => ({
    label: showTenant
      ? t('page.tenantAdminOption', { tenantName: user.tenantName, username: user.username })
      : `${user.username}${user.nickname ? ` (${user.nickname})` : ''}`,
    value: user.id,
  }))
}

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<TenantVo>(getTenantPage, {
    buildParams: (query) => ({ tenantName: (query.tenantName as string) || undefined }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    tenantName: '',
    tenantCode: '',
    contactName: '',
    contactPhone: '',
    userLimit: 100,
    storageLimitMb: 1024,
    adminUserId: undefined,
    status: 1,
  })
  modalOpen.value = true
  loadCandidates()
}
function openEdit(record: TenantVo) {
  editingId.value = record.id
  Object.assign(form, {
    tenantName: record.tenantName,
    tenantCode: record.tenantCode,
    contactName: record.contactName || '',
    contactPhone: record.contactPhone || '',
    userLimit: record.userLimit || 100,
    storageLimitMb: record.storageLimitMb || 1024,
    adminUserId: record.adminUserId,
    status: record.status,
  })
  modalOpen.value = true
  loadCandidates(record.id)
}

async function loadCandidates(tenantId?: number) {
  try {
    const candidates = tenantId
      ? await getTenantUsers(tenantId)
      : await getTenantAdminCandidates()
    userOptions.value = toUserOptions(candidates, !tenantId)
  } catch {
    userOptions.value = []
  }
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
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

