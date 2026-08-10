<template>
  <a-card :title="t('page.deviceTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'device:device:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.deviceAdd') }}
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
          <a-switch
            :checked="record.status === 1"
            :disabled="!canChangeStatus"
            @change="(checked: boolean) => onStatusChange(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'device:device:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'device:device:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.deviceEdit') : t('page.deviceAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.deviceCode')" required>
          <a-input v-model:value="form.deviceCode" :maxlength="50" />
        </a-form-item>
        <a-form-item :label="t('page.deviceName')" required>
          <a-input v-model:value="form.deviceName" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.deviceType')">
          <a-input v-model:value="form.deviceType" :maxlength="50" />
        </a-form-item>
        <a-form-item :label="t('page.deviceLocation')">
          <a-input v-model:value="form.location" :maxlength="200" />
        </a-form-item>
        <a-form-item :label="t('page.deviceSort')">
          <a-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item :label="t('page.deviceStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.deviceDescription')">
          <a-textarea v-model:value="form.description" :rows="3" :maxlength="255" />
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
import { createDevice, deleteDevice, getDevicePage, updateDevice, updateDeviceStatus } from '@/api/device'
import type { DeviceSaveRequest, DeviceVo } from '@/api/device'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canChangeStatus = computed(() => permissionStore.perms.includes('device:device:status'))

const searchFields: SearchField[] = [
  {
    label: t('page.deviceCode'),
    prop: 'deviceCode',
    placeholder: `${t('common.inputPlaceholder')}${t('page.deviceCode')}`,
  },
  {
    label: t('page.deviceName'),
    prop: 'deviceName',
    placeholder: `${t('common.inputPlaceholder')}${t('page.deviceName')}`,
  },
  {
    label: t('page.deviceStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.deviceCode'), dataIndex: 'deviceCode', key: 'deviceCode' },
  { title: t('page.deviceName'), dataIndex: 'deviceName', key: 'deviceName' },
  { title: t('page.deviceType'), dataIndex: 'deviceType', key: 'deviceType', width: 110 },
  { title: t('page.deviceLocation'), dataIndex: 'location', key: 'location', ellipsis: true },
  { title: t('page.deviceSort'), dataIndex: 'sort', key: 'sort', width: 80 },
  { title: t('page.deviceStatus'), dataIndex: 'status', key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<DeviceSaveRequest>({
  deviceCode: '',
  deviceName: '',
  deviceType: '',
  location: '',
  sort: 0,
  description: '',
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<DeviceVo>(getDevicePage, {
    buildParams: (query) => ({
      deviceCode: (query.deviceCode as string) || undefined,
      deviceName: (query.deviceName as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    deviceCode: '',
    deviceName: '',
    deviceType: '',
    location: '',
    sort: 0,
    description: '',
    status: 1,
  })
  modalOpen.value = true
}

function openEdit(record: DeviceVo) {
  editingId.value = record.id
  Object.assign(form, {
    deviceCode: record.deviceCode,
    deviceName: record.deviceName,
    deviceType: record.deviceType || '',
    location: record.location || '',
    sort: record.sort,
    description: record.description || '',
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.deviceCode || !form.deviceName) {
    message.warning(t('page.deviceRequired'))
    return
  }
  saving.value = true
  try {
    const payload: DeviceSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateDevice(payload)
    } else {
      await createDevice(payload)
    }
    message.success(t('page.deviceSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onStatusChange(record: DeviceVo, checked: boolean) {
  const next = checked ? 1 : 0
  const previous = record.status
  record.status = next
  try {
    await updateDeviceStatus(record.id, next)
    message.success(t('page.deviceStatusChanged'))
  } catch {
    record.status = previous
  }
}

function onDelete(record: DeviceVo) {
  Modal.confirm({
    title: t('page.deviceDeleteTitle'),
    content: t('page.deviceDeleteConfirm', { name: record.deviceName }),
    onOk: async () => {
      await deleteDevice(record.id)
      message.success(t('page.deviceDeleted'))
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
