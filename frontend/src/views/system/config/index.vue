<template>
  <a-card :title="t('page.configTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:config:add'" type="primary" @click="openCreate">{{ t('page.configAdd') }}</a-button>
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
        <template v-if="column.key === 'configType'">
          <a-tag :color="record.configType === 0 ? 'blue' : 'default'">
            {{ record.configType === 0 ? t('page.configTypeSystem') : t('page.configTypeCustom') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:config:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:config:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.configEdit') : t('page.configAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.configName')" required>
          <a-input v-model:value="form.configName" />
        </a-form-item>
        <a-form-item :label="t('page.configKey')" required>
          <a-input v-model:value="form.configKey" />
        </a-form-item>
        <a-form-item :label="t('page.configValue')" required>
          <a-input v-model:value="form.configValue" />
        </a-form-item>
        <a-form-item :label="t('page.configType')">
          <a-select v-model:value="form.configType" :options="typeOptions" />
        </a-form-item>
        <a-form-item :label="t('page.configRemark')">
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
import { createConfig, deleteConfig, getConfigPage, updateConfig } from '@/api/system'
import type { ConfigVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.configName'), prop: 'configName', placeholder: `${t('common.inputPlaceholder')}${t('page.configName')}` },
  { label: t('page.configKey'), prop: 'configKey', placeholder: `${t('common.inputPlaceholder')}${t('page.configKey')}` },
]

const columns = [
  { title: t('page.configName'), dataIndex: 'configName', key: 'configName' },
  { title: t('page.configKey'), dataIndex: 'configKey', key: 'configKey' },
  { title: t('page.configValue'), dataIndex: 'configValue', key: 'configValue' },
  { title: t('page.configType'), key: 'configType', width: 100 },
  { title: t('page.configRemark'), dataIndex: 'remark', key: 'remark' },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const typeOptions = [
  { label: t('page.configTypeSystem'), value: 0 },
  { label: t('page.configTypeCustom'), value: 1 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const records = ref<ConfigVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({
  configName: '',
  configKey: '',
  configValue: '',
  configType: 1,
  remark: '',
})

async function loadData() {
  loading.value = true
  try {
    const data = await getConfigPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      configName: (searchModel.configName as string) || undefined,
      configKey: (searchModel.configKey as string) || undefined,
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
  Object.keys(searchModel).forEach((key) => {
    searchModel[key] = undefined
  })
  pageNum.value = 1
  loadData()
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { configName: '', configKey: '', configValue: '', configType: 1, remark: '' })
  modalOpen.value = true
}

function openEdit(record: ConfigVo) {
  editingId.value = record.id
  Object.assign(form, {
    configName: record.configName,
    configKey: record.configKey,
    configValue: record.configValue,
    configType: record.configType,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.configName || !form.configKey || !form.configValue) {
    message.warning(t('page.configRequired'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateConfig({ ...form, id: editingId.value })
    } else {
      await createConfig(form)
    }
    message.success(t('page.configSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: ConfigVo) {
  Modal.confirm({
    title: t('page.configDeleteTitle'),
    content: t('page.configDeleteConfirm', { name: record.configName }),
    onOk: async () => {
      await deleteConfig(record.id)
      message.success(t('page.configDeleted'))
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

