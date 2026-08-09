<template>
  <a-card title="参数配置">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:config:add'" type="primary" @click="openCreate">新增参数</a-button>
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
            {{ record.configType === 0 ? '系统内置' : '自定义' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:config:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:config:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑参数' : '新增参数'"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="参数名称" required>
          <a-input v-model:value="form.configName" />
        </a-form-item>
        <a-form-item label="参数键名" required>
          <a-input v-model:value="form.configKey" />
        </a-form-item>
        <a-form-item label="参数键值" required>
          <a-input v-model:value="form.configValue" />
        </a-form-item>
        <a-form-item label="系统内置">
          <a-select v-model:value="form.configType" :options="typeOptions" />
        </a-form-item>
        <a-form-item label="备注">
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

const searchFields: SearchField[] = [
  { label: '参数名称', prop: 'configName', placeholder: '请输入参数名称' },
  { label: '参数键名', prop: 'configKey', placeholder: '请输入参数键名' },
]

const columns = [
  { title: '参数名称', dataIndex: 'configName', key: 'configName' },
  { title: '参数键名', dataIndex: 'configKey', key: 'configKey' },
  { title: '参数键值', dataIndex: 'configValue', key: 'configValue' },
  { title: '类型', key: 'configType', width: 100 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'actions', width: 130 },
]

const typeOptions = [
  { label: '系统内置', value: 0 },
  { label: '自定义', value: 1 },
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
    message.warning('请填写参数名称、键名和键值')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateConfig({ ...form, id: editingId.value })
    } else {
      await createConfig(form)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: ConfigVo) {
  Modal.confirm({
    title: '确认删除参数',
    content: `确定删除参数 ${record.configName} 吗？`,
    onOk: async () => {
      await deleteConfig(record.id)
      message.success('删除成功')
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

