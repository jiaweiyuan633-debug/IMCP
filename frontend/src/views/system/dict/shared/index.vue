<template>
  <a-card :title="t('page.sharedDictTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:dict:shared:add'" type="primary" @click="openCreate">{{ t('page.sharedDictAdd') }}</a-button>
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
            <a v-permission="'system:dict:shared:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.sharedDictEdit') : t('page.sharedDictAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.dictName')" required>
          <a-input v-model:value="form.dictName" />
        </a-form-item>
        <a-form-item :label="t('page.dictType')" required>
          <a-input v-model:value="form.dictType" />
        </a-form-item>
        <a-form-item :label="t('page.dictStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.dictRemark')">
          <a-textarea v-model:value="form.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { createSharedDictType, getSharedDictTypePage, updateSharedDictType } from '@/api/system'
import type { DictTypeVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.dictName'), prop: 'dictName', placeholder: `${t('common.inputPlaceholder')}${t('page.dictName')}` },
  { label: t('page.dictType'), prop: 'dictType', placeholder: `${t('common.inputPlaceholder')}${t('page.dictType')}` },
  {
    label: t('page.dictStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.dictName'), dataIndex: 'dictName', key: 'dictName' },
  { title: t('page.dictType'), dataIndex: 'dictType', key: 'dictType' },
  { title: t('page.dictStatus'), key: 'status', width: 90 },
  { title: t('page.dictRemark'), dataIndex: 'remark', key: 'remark', ellipsis: true },
  dateColumn('createdAt', { title: t('common.createdAt'), width: 170 }),
  { title: t('common.actions'), key: 'actions', width: 90 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({ dictName: '', dictType: '', status: 1, remark: '' })

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<DictTypeVo>(getSharedDictTypePage, {
    buildParams: (query) => ({
      dictName: (query.dictName as string) || undefined,
      dictType: (query.dictType as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { dictName: '', dictType: '', status: 1, remark: '' })
  modalOpen.value = true
}

function openEdit(record: DictTypeVo) {
  editingId.value = record.id
  Object.assign(form, {
    dictName: record.dictName,
    dictType: record.dictType,
    status: record.status,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.dictName || !form.dictType) {
    message.warning(t('page.dictTypeRequired'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateSharedDictType({ ...form, id: editingId.value })
    } else {
      await createSharedDictType(form)
    }
    message.success(t('page.dictSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
