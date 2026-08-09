<template>
  <a-card title="字典管理">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:dict:add'" type="primary" @click="openTypeCreate">新增字典类型</a-button>
    </div>
    <ProTable
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :columns="typeColumns"
      :data-source="typeRecords"
      :loading="loading"
      :total="total"
      row-key="id"
      @change="loadTypes"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:dict:list'" @click="openData(record)">数据</a>
            <a v-permission="'system:dict:edit'" @click="openTypeEdit(record)">编辑</a>
            <a v-permission="'system:dict:delete'" @click="onTypeDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="typeModalOpen"
      :title="typeEditingId ? '编辑字典类型' : '新增字典类型'"
      :loading="saving"
      @ok="onTypeSubmit"
    >
      <a-form layout="vertical" :model="typeForm">
        <a-form-item label="字典名称" required>
          <a-input v-model:value="typeForm.dictName" />
        </a-form-item>
        <a-form-item label="字典类型" required>
          <a-input v-model:value="typeForm.dictType" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="typeForm.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="typeForm.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="dataModalOpen" title="字典数据" width="860" :footer="null">
      <div class="toolbar">
        <a-button v-permission="'system:dict:data:add'" type="primary" @click="openDataCreate">新增数据</a-button>
      </div>
      <ProTable
        v-model:page-num="dataPageNum"
        v-model:page-size="dataPageSize"
        :columns="dataColumns"
        :data-source="dataRecords"
        :loading="dataLoading"
        :total="dataTotal"
        row-key="id"
        @change="loadData"
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
              <a v-permission="'system:dict:data:edit'" @click="openDataEdit(record)">编辑</a>
              <a v-permission="'system:dict:data:delete'" @click="onDataDelete(record)">删除</a>
            </a-space>
          </template>
        </template>
      </ProTable>
    </a-modal>

    <ModalForm
      v-model:open="dataFormOpen"
      :title="dataEditingId ? '编辑字典数据' : '新增字典数据'"
      :loading="saving"
      width="520"
      @ok="onDataSubmit"
    >
      <a-form layout="vertical" :model="dataForm">
        <a-form-item label="字典标签" required>
          <a-input v-model:value="dataForm.dictLabel" />
        </a-form-item>
        <a-form-item label="字典键值" required>
          <a-input v-model:value="dataForm.dictValue" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="dataForm.dictSort" />
        </a-form-item>
        <a-form-item label="标签样式">
          <a-input v-model:value="dataForm.listClass" placeholder="success/danger/warning" />
        </a-form-item>
        <a-form-item label="默认">
          <a-select v-model:value="dataForm.isDefault" :options="defaultOptions" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="dataForm.status" :options="statusOptions" />
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

const searchFields: SearchField[] = [
  { label: '字典名称', prop: 'dictName', placeholder: '请输入字典名称' },
  { label: '字典类型', prop: 'dictType', placeholder: '请输入字典类型' },
]

const typeColumns = [
  { title: '字典名称', dataIndex: 'dictName', key: 'dictName' },
  { title: '字典类型', dataIndex: 'dictType', key: 'dictType' },
  { title: '状态', key: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'actions', width: 180 },
]

const dataColumns = [
  { title: '字典标签', dataIndex: 'dictLabel', key: 'dictLabel' },
  { title: '字典键值', dataIndex: 'dictValue', key: 'dictValue' },
  { title: '排序', dataIndex: 'dictSort', key: 'dictSort', width: 80 },
  { title: '默认', key: 'isDefault', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 130 },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const defaultOptions = [
  { label: '是', value: 1 },
  { label: '否', value: 0 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const typeRecords = ref<DictTypeVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const typeModalOpen = ref(false)
const typeEditingId = ref<number | undefined>()
const typeForm = reactive({ dictName: '', dictType: '', status: 1, remark: '' })

const dataModalOpen = ref(false)
const dataFormOpen = ref(false)
const dataPageNum = ref(1)
const dataPageSize = ref(10)
const dataTotal = ref(0)
const dataLoading = ref(false)
const dataRecords = ref<DictDataVo[]>([])
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

async function loadTypes() {
  loading.value = true
  try {
    const data = await getDictTypePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      dictName: (searchModel.dictName as string) || undefined,
      dictType: (searchModel.dictType as string) || undefined,
    })
    typeRecords.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onSearch(model: Record<string, unknown>) {
  Object.assign(searchModel, model)
  pageNum.value = 1
  loadTypes()
}

function onReset() {
  Object.keys(searchModel).forEach((key) => {
    searchModel[key] = undefined
  })
  pageNum.value = 1
  loadTypes()
}

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
    message.warning('请填写字典名称和类型')
    return
  }
  saving.value = true
  try {
    if (typeEditingId.value) {
      await updateDictType({ ...typeForm, id: typeEditingId.value })
    } else {
      await createDictType(typeForm)
    }
    message.success('保存成功')
    typeModalOpen.value = false
    loadTypes()
  } finally {
    saving.value = false
  }
}

function onTypeDelete(record: DictTypeVo) {
  Modal.confirm({
    title: '确认删除字典类型',
    content: `删除 ${record.dictName} 会同时删除其字典数据，确定吗？`,
    onOk: async () => {
      await deleteDictType(record.id)
      message.success('删除成功')
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

async function loadData() {
  dataLoading.value = true
  try {
    const data = await getDictDataPage({
      pageNum: dataPageNum.value,
      pageSize: dataPageSize.value,
      dictType: currentDictType.value,
    })
    dataRecords.value = data.records
    dataTotal.value = data.total
  } finally {
    dataLoading.value = false
  }
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
    message.warning('请填写字典标签和键值')
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
    message.success('保存成功')
    dataFormOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDataDelete(record: DictDataVo) {
  Modal.confirm({
    title: '确认删除字典数据',
    content: `确定删除 ${record.dictLabel} 吗？`,
    onOk: async () => {
      await deleteDictData(record.id)
      message.success('删除成功')
      loadData()
    },
  })
}

loadTypes()
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

