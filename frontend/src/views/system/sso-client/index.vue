<template>
  <a-card :title="t('page.oauthClientTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:oauth:client:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.oauthClientAdd') }}
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
        <template v-if="column.key === 'clientId'">
          <a-typography-text code>{{ record.clientId }}</a-typography-text>
        </template>
        <template v-else-if="column.key === 'clientSecret'">
          <a-typography-text type="secondary">••••••••</a-typography-text>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <a-switch
            :checked="record.enabled === 1"
            :disabled="!canChangeStatus"
            @change="(checked: boolean) => onStatusChange(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:oauth:client:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:oauth:client:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.oauthClientEdit') : t('page.oauthClientAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.oauthClientName')" required>
          <a-input v-model:value="form.clientName" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientId')" required>
          <a-input v-model:value="form.clientId" :maxlength="64" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientSecret')" required>
          <a-input-password v-model:value="form.clientSecret" :maxlength="128" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientRedirectUri')">
          <a-input v-model:value="form.redirectUri" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientScope')">
          <a-input v-model:value="form.scope" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientStatus')">
          <a-select v-model:value="form.enabled" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientSort')">
          <a-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item :label="t('page.oauthClientRemark')">
          <a-textarea v-model:value="form.remark" :rows="3" :maxlength="255" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { usePermissionStore } from '@/stores/permission'
import {
  createOauthClient,
  deleteOauthClient,
  getOauthClientPage,
  updateOauthClient,
  updateOauthClientStatus,
} from '@/api/oauth'
import type { OauthClientSaveRequest, OauthClientVo } from '@/api/oauth'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canChangeStatus = computed(() => permissionStore.perms.includes('system:oauth:client:status'))

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const searchFields: SearchField[] = [
  { label: t('page.oauthClientName'), prop: 'clientName', placeholder: `${t('common.inputPlaceholder')}${t('page.oauthClientName')}` },
  {
    label: t('page.oauthClientStatus'),
    prop: 'enabled',
    type: 'select',
    options: statusOptions,
  },
]

const columns = [
  { title: t('page.oauthClientName'), dataIndex: 'clientName', key: 'clientName', ellipsis: true },
  { title: t('page.oauthClientId'), dataIndex: 'clientId', key: 'clientId', ellipsis: true },
  { title: t('page.oauthClientSecret'), dataIndex: 'clientSecret', key: 'clientSecret', width: 120 },
  { title: t('page.oauthClientRedirectUri'), dataIndex: 'redirectUri', key: 'redirectUri', ellipsis: true },
  { title: t('page.oauthClientStatus'), dataIndex: 'enabled', key: 'enabled', width: 90 },
  { title: t('page.oauthClientSort'), dataIndex: 'sort', key: 'sort', width: 70 },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<OauthClientSaveRequest>({
  clientName: '',
  clientId: '',
  clientSecret: '',
  redirectUri: '',
  scope: '',
  enabled: 1,
  sort: 0,
  remark: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<OauthClientVo>(getOauthClientPage, {
    buildParams: (query) => ({
      clientName: (query.clientName as string) || undefined,
      enabled: query.enabled as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    clientName: '',
    clientId: '',
    clientSecret: '',
    redirectUri: '',
    scope: '',
    enabled: 1,
    sort: 0,
    remark: '',
  })
  modalOpen.value = true
}

function openEdit(record: OauthClientVo) {
  editingId.value = record.id
  Object.assign(form, {
    clientName: record.clientName,
    clientId: record.clientId,
    clientSecret: record.clientSecret,
    redirectUri: record.redirectUri || '',
    scope: record.scope || '',
    enabled: record.enabled,
    sort: record.sort,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.clientName || !form.clientId || !form.clientSecret) {
    message.warning(t('page.oauthClientRequired'))
    return
  }
  saving.value = true
  try {
    const payload: OauthClientSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateOauthClient(payload)
    } else {
      await createOauthClient(payload)
    }
    message.success(t('page.oauthClientSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onStatusChange(record: OauthClientVo, checked: boolean) {
  const next = checked ? 1 : 0
  const previous = record.enabled
  record.enabled = next
  try {
    await updateOauthClientStatus(record.id, next)
  } catch {
    record.enabled = previous
  }
}

function onDelete(record: OauthClientVo) {
  Modal.confirm({
    title: t('page.oauthClientDeleteTitle'),
    content: t('page.oauthClientDeleteConfirm', { name: record.clientName }),
    onOk: async () => {
      await deleteOauthClient(record.id)
      message.success(t('page.oauthClientDeleted'))
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
