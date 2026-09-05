<template>
  <a-card :title="t('page.apiPermTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:api-perm:reload'" @click="onReload">{{ t('page.apiPermReload') }}</a-button>
      <a-button v-permission="'system:api-perm:add'" type="primary" @click="openCreate">{{ t('page.apiPermAdd') }}</a-button>
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
        <template v-if="column.key === 'method'">
          <a-tag :color="record.method === '*' ? 'default' : 'blue'">{{ record.method }}</a-tag>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <StatusTag :value="record.enabled" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:api-perm:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:api-perm:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.apiPermEdit') : t('page.apiPermAdd')"
      :loading="saving"
      width="560"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.apiPermMethod')" required>
          <a-select v-model:value="form.method" :options="methodOptions" />
        </a-form-item>
        <a-form-item :label="t('page.apiPermPathPattern')" required>
          <a-input v-model:value="form.pathPattern" :placeholder="t('page.apiPermPathPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.apiPermPermCode')" required>
          <a-input v-model:value="form.permCode" :placeholder="t('page.apiPermPermPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.apiPermEnabled')">
          <a-switch v-model:checked="form.enabled" :checked-value="1" :un-checked-value="0" />
        </a-form-item>
        <a-form-item :label="t('page.apiPermRemark')">
          <a-textarea v-model:value="form.remark" :rows="2" />
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
import StatusTag from '@/components/StatusTag.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { createApiPerm, deleteApiPerm, getApiPermPage, reloadApiPerm, updateApiPerm } from '@/api/system'
import type { ApiPermSaveRequest, ApiPermVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  {
    label: t('page.apiPermMethod'),
    prop: 'method',
    type: 'select',
    options: [
      { label: 'GET', value: 'GET' },
      { label: 'POST', value: 'POST' },
      { label: 'PUT', value: 'PUT' },
      { label: 'DELETE', value: 'DELETE' },
      { label: 'PATCH', value: 'PATCH' },
      { label: '*', value: '*' },
    ],
  },
  { label: t('page.apiPermPathPattern'), prop: 'pathPattern', placeholder: `${t('common.inputPlaceholder')}${t('page.apiPermPathPattern')}` },
  {
    label: t('page.apiPermEnabled'),
    prop: 'enabled',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const methodOptions = searchFields[0].options as { label: string; value: string }[]

const columns = [
  { title: t('page.apiPermMethod'), key: 'method', width: 100 },
  { title: t('page.apiPermPathPattern'), dataIndex: 'pathPattern', key: 'pathPattern', ellipsis: true },
  { title: t('page.apiPermPermCode'), dataIndex: 'permCode', key: 'permCode', width: 200 },
  { title: t('page.apiPermEnabled'), key: 'enabled', width: 90 },
  { title: t('page.apiPermRemark'), dataIndex: 'remark', key: 'remark', ellipsis: true },
  dateColumn('createdAt', { title: t('common.createdAt'), width: 170 }),
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({
  method: 'POST',
  pathPattern: '',
  permCode: '',
  enabled: 1,
  remark: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ApiPermVo>(getApiPermPage, {
    buildParams: (query) => ({
      method: (query.method as string) || undefined,
      pathPattern: (query.pathPattern as string) || undefined,
      enabled: query.enabled as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { method: 'POST', pathPattern: '', permCode: '', enabled: 1, remark: '' })
  modalOpen.value = true
}

function openEdit(record: ApiPermVo) {
  editingId.value = record.id
  Object.assign(form, {
    method: record.method,
    pathPattern: record.pathPattern,
    permCode: record.permCode,
    enabled: record.enabled,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.pathPattern || !form.permCode) {
    message.warning(t('page.apiPermRequired'))
    return
  }
  saving.value = true
  try {
    const payload: ApiPermSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateApiPerm(payload)
    } else {
      await createApiPerm(payload)
    }
    message.success(t('page.apiPermSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: ApiPermVo) {
  Modal.confirm({
    title: t('page.apiPermDeleteTitle'),
    content: t('page.apiPermDeleteConfirm', { path: record.pathPattern }),
    onOk: async () => {
      await deleteApiPerm(record.id)
      message.success(t('page.apiPermDeleted'))
      loadData()
    },
  })
}

async function onReload() {
  await reloadApiPerm()
  message.success(t('page.apiPermReloaded'))
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
}
.danger {
  color: #ff4d4f;
}
</style>
