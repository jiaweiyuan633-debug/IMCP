<template>
  <a-card :title="t('page.oauthConfigTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:oauth:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.oauthConfigAdd') }}
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
        <template v-if="column.key === 'provider'">
          <a-tag color="blue">{{ record.providerLabel }}</a-tag>
          <span class="provider-code">{{ record.provider }}</span>
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
            <a v-permission="'system:oauth:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:oauth:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.oauthConfigEdit') : t('page.oauthConfigAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.oauthConfigProvider')" required>
          <a-select v-model:value="form.provider" :options="providerOptions" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigAppId')" required>
          <a-input v-model:value="form.appId" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigAppSecret')" required>
          <a-input-password v-model:value="form.appSecret" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigRedirectUri')">
          <a-input v-model:value="form.redirectUri" :placeholder="redirectPlaceholder" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigScope')">
          <a-input v-model:value="form.scope" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigStatus')">
          <a-select v-model:value="form.enabled" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigSort')">
          <a-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item :label="t('page.oauthConfigRemark')">
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
  createOauthConfig,
  deleteOauthConfig,
  getOauthConfigPage,
  updateOauthConfig,
  updateOauthConfigStatus,
} from '@/api/oauth'
import type { OauthConfigSaveRequest, OauthConfigVo } from '@/api/oauth'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canChangeStatus = computed(() => permissionStore.perms.includes('system:oauth:status'))

const providerOptions = [
  { label: t('page.oauthProviderWechat'), value: 'wechat' },
  { label: t('page.oauthProviderGithub'), value: 'github' },
  { label: t('page.oauthProviderGitee'), value: 'gitee' },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const searchFields: SearchField[] = [
  {
    label: t('page.oauthConfigProvider'),
    prop: 'provider',
    type: 'select',
    options: providerOptions,
  },
  {
    label: t('page.oauthConfigStatus'),
    prop: 'enabled',
    type: 'select',
    options: statusOptions,
  },
]

const columns = [
  { title: t('page.oauthConfigProvider'), dataIndex: 'provider', key: 'provider', width: 180 },
  { title: t('page.oauthConfigAppId'), dataIndex: 'appId', key: 'appId', ellipsis: true },
  { title: t('page.oauthConfigRedirectUri'), dataIndex: 'redirectUri', key: 'redirectUri', ellipsis: true },
  { title: t('page.oauthConfigStatus'), dataIndex: 'enabled', key: 'enabled', width: 90 },
  { title: t('page.oauthConfigSort'), dataIndex: 'sort', key: 'sort', width: 70 },
  { title: t('page.oauthConfigRemark'), dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<OauthConfigSaveRequest>({
  provider: 'wechat',
  appId: '',
  appSecret: '',
  redirectUri: '',
  scope: '',
  enabled: 1,
  sort: 0,
  remark: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<OauthConfigVo>(getOauthConfigPage, {
    buildParams: (query) => ({
      provider: (query.provider as string) || undefined,
      enabled: query.enabled as number | undefined,
    }),
  })

function redirectPlaceholder(): string {
  return `${window.location.origin}/api/auth/oauth/callback/${form.provider}`
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    provider: 'wechat',
    appId: '',
    appSecret: '',
    redirectUri: '',
    scope: '',
    enabled: 1,
    sort: 0,
    remark: '',
  })
  modalOpen.value = true
}

function openEdit(record: OauthConfigVo) {
  editingId.value = record.id
  Object.assign(form, {
    provider: record.provider,
    appId: record.appId,
    appSecret: record.appSecret,
    redirectUri: record.redirectUri || '',
    scope: record.scope || '',
    enabled: record.enabled,
    sort: record.sort,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.provider || !form.appId || !form.appSecret) {
    message.warning(t('page.oauthConfigRequired'))
    return
  }
  saving.value = true
  try {
    const payload: OauthConfigSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateOauthConfig(payload)
    } else {
      await createOauthConfig(payload)
    }
    message.success(t('page.oauthConfigSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onStatusChange(record: OauthConfigVo, checked: boolean) {
  const next = checked ? 1 : 0
  const previous = record.enabled
  record.enabled = next
  try {
    await updateOauthConfigStatus(record.id, next)
  } catch {
    record.enabled = previous
  }
}

function onDelete(record: OauthConfigVo) {
  Modal.confirm({
    title: t('page.oauthConfigDeleteTitle'),
    content: t('page.oauthConfigDeleteConfirm', { name: record.providerLabel }),
    onOk: async () => {
      await deleteOauthConfig(record.id)
      message.success(t('page.oauthConfigDeleted'))
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
.provider-code {
  margin-left: 8px;
  color: #999;
  font-size: 12px;
}
</style>
