<template>
  <a-card :title="t('page.thingModelTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'device:thing-model:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.thingModelAdd') }}
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
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? t('common.enabled') : t('common.disabled') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'device:thing-model:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'device:thing-model:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.thingModelEdit') : t('page.thingModelAdd')"
      :loading="saving"
      :width="720"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.thingModelDeviceType')" required>
          <a-input v-model:value="form.deviceType" :maxlength="64" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item :label="t('page.thingModelName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.thingModelDescription')">
          <a-textarea v-model:value="form.description" :rows="2" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.thingModelProperties')">
          <a-textarea
            v-model:value="form.propertiesJson"
            :rows="4"
            placeholder='[{"key":"temperature","name":"温度","dataType":"number","unit":"℃","mode":"rw"}]'
          />
        </a-form-item>
        <a-form-item :label="t('page.thingModelEvents')">
          <a-textarea
            v-model:value="form.eventsJson"
            :rows="3"
            placeholder='[{"key":"alarm","name":"告警","params":[{"key":"level","name":"级别"}]}]'
          />
        </a-form-item>
        <a-form-item :label="t('page.thingModelServices')">
          <a-textarea
            v-model:value="form.servicesJson"
            :rows="3"
            placeholder='[{"key":"reboot","name":"重启","params":[]}]'
          />
        </a-form-item>
        <a-form-item :label="t('page.thingModelStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import {
  createThingModel,
  deleteThingModel,
  getThingModelPage,
  updateThingModel,
} from '@/api/thingModel'
import type { ThingModelSaveRequest, ThingModelVo } from '@/api/thingModel'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.thingModelDeviceType'), prop: 'deviceType', placeholder: `${t('common.inputPlaceholder')}${t('page.thingModelDeviceType')}` },
  { label: t('page.thingModelName'), prop: 'name', placeholder: `${t('common.inputPlaceholder')}${t('page.thingModelName')}` },
  {
    label: t('page.thingModelStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.thingModelDeviceType'), dataIndex: 'deviceType', key: 'deviceType', width: 140 },
  { title: t('page.thingModelName'), dataIndex: 'name', key: 'name' },
  { title: t('page.thingModelDescription'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('page.thingModelStatus'), dataIndex: 'status', key: 'status', width: 90 },
  dateColumn('createdAt', { title: t('page.thingModelCreatedAt'), width: 170 }),
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<ThingModelSaveRequest>({
  deviceType: '',
  name: '',
  description: '',
  propertiesJson: '',
  eventsJson: '',
  servicesJson: '',
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ThingModelVo>(getThingModelPage, {
    buildParams: (query) => ({
      deviceType: (query.deviceType as string) || undefined,
      name: (query.name as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    deviceType: '',
    name: '',
    description: '',
    propertiesJson: '',
    eventsJson: '',
    servicesJson: '',
    status: 1,
    version: undefined,
  })
  modalOpen.value = true
}

function openEdit(record: ThingModelVo) {
  editingId.value = record.id
  Object.assign(form, {
    deviceType: record.deviceType,
    name: record.name,
    description: record.description || '',
    propertiesJson: record.propertiesJson || '',
    eventsJson: record.eventsJson || '',
    servicesJson: record.servicesJson || '',
    status: record.status,
    version: record.version,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.deviceType || !form.name) {
    message.warning(t('page.thingModelRequired'))
    return
  }
  // JSON 字段合法性预检，避免入库坏 JSON
  for (const key of ['propertiesJson', 'eventsJson', 'servicesJson'] as const) {
    const raw = form[key]
    if (raw) {
      try {
        JSON.parse(raw)
      } catch {
        message.error(t('page.thingModelJsonInvalid', { field: t(`page.thingModel${key === 'propertiesJson' ? 'Properties' : key === 'eventsJson' ? 'Events' : 'Services'}`) }))
        return
      }
    }
  }
  saving.value = true
  try {
    const payload: ThingModelSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateThingModel(payload)
    } else {
      await createThingModel(payload)
    }
    message.success(t('page.thingModelSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: ThingModelVo) {
  Modal.confirm({
    title: t('page.thingModelDeleteTitle'),
    content: t('page.thingModelDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteThingModel(record.id)
      message.success(t('page.thingModelDeleted'))
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
