<template>
  <a-card :title="t('page.dictTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:dict:add'" type="primary" @click="openTypeCreate">{{ t('page.dictAddType') }}</a-button>
    </div>
    <ProTable
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :columns="typeColumns"
      :data-source="typeRecords"
      :loading="loading"
      :total="total"
      row-key="id"
      :error="error"
      @change="loadTypes"
      @retry="loadTypes"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:dict:list'" @click="openData(record)">{{ t('page.dictData') }}</a>
            <a v-permission="'system:dict:edit'" @click="openTypeEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:dict:delete'" @click="onTypeDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="typeModalOpen"
      :title="typeEditingId ? t('page.dictEditType') : t('page.dictAddType')"
      :loading="saving"
      @ok="onTypeSubmit"
    >
      <a-form layout="vertical" :model="typeForm">
        <a-form-item :label="t('page.dictName')" required>
          <a-input v-model:value="typeForm.dictName" />
        </a-form-item>
        <a-form-item :label="t('page.dictType')" required>
          <a-input v-model:value="typeForm.dictType" />
        </a-form-item>
        <a-form-item :label="t('page.dictStatus')">
          <a-select v-model:value="typeForm.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.dictRemark')">
          <a-textarea v-model:value="typeForm.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="dataModalOpen" :title="t('page.dictDataTitle')" width="860" :footer="null">
      <div class="toolbar">
        <a-button v-permission="'system:dict:data:add'" type="primary" @click="openDataCreate">{{ t('page.dictAddData') }}</a-button>
      </div>
      <ProTable
        v-model:page-num="dataPageNum"
        v-model:page-size="dataPageSize"
        :columns="dataColumns"
        :data-source="dataRecords"
        :loading="dataLoading"
        :total="dataTotal"
        row-key="id"
        :error="dataError"
        @change="loadData"
        @retry="loadData"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag :value="record.status" />
          </template>
          <template v-else-if="column.key === 'isDefault'">
            <StatusTag :value="record.isDefault === 1 ? 'Y' : 'N'" />
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a v-permission="'system:dict:data:edit'" @click="openDataEdit(record)">{{ t('common.edit') }}</a>
              <a v-permission="'system:dict:data:delete'" @click="onDataDelete(record)">{{ t('common.delete') }}</a>
            </a-space>
          </template>
        </template>
      </ProTable>
    </a-modal>

    <ModalForm
      v-model:open="dataFormOpen"
      :title="dataEditingId ? t('page.dictEditData') : t('page.dictAddData')"
      :loading="saving"
      width="520"
      @ok="onDataSubmit"
    >
      <a-form layout="vertical" :model="dataForm">
        <a-form-item :label="t('page.dictLabel')" required>
          <a-input v-model:value="dataForm.dictLabel" />
        </a-form-item>
        <a-form-item :label="t('page.dictValue')" required>
          <a-input v-model:value="dataForm.dictValue" />
        </a-form-item>
        <a-form-item :label="t('page.dictSort')">
          <a-input-number v-model:value="dataForm.dictSort" />
        </a-form-item>
        <a-form-item :label="t('page.dictListClass')">
          <a-input v-model:value="dataForm.listClass" placeholder="success/danger/warning" />
        </a-form-item>
        <a-form-item :label="t('page.dictDefault')">
          <a-select v-model:value="dataForm.isDefault" :options="defaultOptions" />
        </a-form-item>
        <a-form-item :label="t('page.dictStatus')">
          <a-select v-model:value="dataForm.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useTableQuery } from '@/composables/useTableQuery'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  createDictData,
  createDictType,
  deleteDictData,
  deleteDictType,
  getDictDataPage,
  getDictTypePage,
  updateDictData,
  updateDictType,
} from '@/api/system'
import type { DictDataVo, DictTypeVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.dictName'), prop: 'dictName', placeholder: `${t('common.inputPlaceholder')}${t('page.dictName')}` },
  { label: t('page.dictType'), prop: 'dictType', placeholder: `${t('common.inputPlaceholder')}${t('page.dictType')}` },
]

const typeColumns = [
  { title: t('page.dictName'), dataIndex: 'dictName', key: 'dictName' },
  { title: t('page.dictType'), dataIndex: 'dictType', key: 'dictType' },
  { title: t('page.dictStatus'), key: 'status', width: 90 },
  { title: t('page.dictRemark'), dataIndex: 'remark', key: 'remark' },
  { title: t('common.actions'), key: 'actions', width: 180 },
]

const dataColumns = [
  { title: t('page.dictLabel'), dataIndex: 'dictLabel', key: 'dictLabel' },
  { title: t('page.dictValue'), dataIndex: 'dictValue', key: 'dictValue' },
  { title: t('page.dictSort'), dataIndex: 'dictSort', key: 'dictSort', width: 80 },
  { title: t('page.dictDefault'), key: 'isDefault', width: 80 },
  { title: t('page.dictStatus'), key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const defaultOptions = [
  { label: t('page.yes'), value: 1 },
  { label: t('page.no'), value: 0 },
]

const saving = ref(false)
const typeModalOpen = ref(false)
const typeEditingId = ref<number | undefined>()
const typeForm = reactive({ dictName: '', dictType: '', status: 1, remark: '' })

const dataModalOpen = ref(false)
const dataFormOpen = ref(false)
const currentDictType = ref('')
const dataEditingId = ref<number | undefined>()
const dataForm = reactive({
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  listClass: '',
  isDefault: 0,
  status: 1,
})

const {
  pageNum,
  pageSize,
  total,
  loading,
  records: typeRecords,
  error,
  loadData: loadTypes,
  onSearch,
  onReset,
} = useTableQuery<DictTypeVo>(getDictTypePage, {
  buildParams: (query) => ({
    dictName: (query.dictName as string) || undefined,
    dictType: (query.dictType as string) || undefined,
  }),
})

const {
  pageNum: dataPageNum,
  pageSize: dataPageSize,
  total: dataTotal,
  loading: dataLoading,
  records: dataRecords,
  error: dataError,
  loadData,
} = useTableQuery<DictDataVo>(getDictDataPage, {
  immediate: false,
  buildParams: () => ({
    dictType: currentDictType.value,
  }),
})

function openTypeCreate() {
  typeEditingId.value = undefined
  Object.assign(typeForm, { dictName: '', dictType: '', status: 1, remark: '' })
  typeModalOpen.value = true
}

function openTypeEdit(record: DictTypeVo) {
  typeEditingId.value = record.id
  Object.assign(typeForm, {
    dictName: record.dictName,
    dictType: record.dictType,
    status: record.status,
    remark: record.remark || '',
  })
  typeModalOpen.value = true
}

async function onTypeSubmit() {
  if (!typeForm.dictName || !typeForm.dictType) {
    message.warning(t('page.dictTypeRequired'))
    return
  }
  saving.value = true
  try {
    if (typeEditingId.value) {
      await updateDictType({ ...typeForm, id: typeEditingId.value })
    } else {
      await createDictType(typeForm)
    }
    message.success(t('page.dictSaved'))
    typeModalOpen.value = false
    loadTypes()
  } finally {
    saving.value = false
  }
}

function onTypeDelete(record: DictTypeVo) {
  Modal.confirm({
    title: t('page.dictTypeDeleteTitle'),
    content: t('page.dictTypeDeleteConfirm', { name: record.dictName }),
    onOk: async () => {
      await deleteDictType(record.id)
      message.success(t('page.dictDeleted'))
      loadTypes()
    },
  })
}

function openData(record: DictTypeVo) {
  currentDictType.value = record.dictType
  dataPageNum.value = 1
  dataModalOpen.value = true
  loadData()
}

function openDataCreate() {
  dataEditingId.value = undefined
  Object.assign(dataForm, { dictLabel: '', dictValue: '', dictSort: 0, listClass: '', isDefault: 0, status: 1 })
  dataFormOpen.value = true
}

function openDataEdit(record: DictDataVo) {
  dataEditingId.value = record.id
  Object.assign(dataForm, {
    dictLabel: record.dictLabel,
    dictValue: record.dictValue,
    dictSort: record.dictSort,
    listClass: record.listClass || '',
    isDefault: record.isDefault,
    status: record.status,
  })
  dataFormOpen.value = true
}

async function onDataSubmit() {
  if (!dataForm.dictLabel || !dataForm.dictValue) {
    message.warning(t('page.dictDataRequired'))
    return
  }
  saving.value = true
  try {
    const payload = { ...dataForm, dictType: currentDictType.value }
    if (dataEditingId.value) {
      await updateDictData({ ...payload, id: dataEditingId.value })
    } else {
      await createDictData(payload)
    }
    message.success(t('page.dictSaved'))
    dataFormOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDataDelete(record: DictDataVo) {
  Modal.confirm({
    title: t('page.dictDataDeleteTitle'),
    content: t('page.dictDataDeleteConfirm', { name: record.dictLabel }),
    onOk: async () => {
      await deleteDictData(record.id)
      message.success(t('page.dictDeleted'))
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

