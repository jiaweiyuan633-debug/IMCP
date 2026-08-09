<template>
  <a-card title="租户管理">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:tenant:add'" type="primary" @click="openCreate">新增租户</a-button>
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
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:tenant:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:tenant:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>
    <ModalForm v-model:open="modalOpen" :title="editingId ? '编辑租户' : '新增租户'" :loading="saving" @ok="onSubmit">
      <a-form layout="vertical" :model="form">
        <a-form-item label="租户名称" required><a-input v-model:value="form.tenantName" /></a-form-item>
        <a-form-item label="租户编码" required><a-input v-model:value="form.tenantCode" /></a-form-item>
        <a-form-item label="联系人"><a-input v-model:value="form.contactName" /></a-form-item>
        <a-form-item label="联系电话"><a-input v-model:value="form.contactPhone" /></a-form-item>
        <a-form-item label="状态"><a-select v-model:value="form.status" :options="statusOptions" /></a-form-item>
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
import { createTenant, deleteTenant, getTenantPage, updateTenant } from '@/api/system'
import type { TenantVo } from '@/api/system'
import type { SearchField } from '@/types'

const searchFields: SearchField[] = [{ label: '租户名称', prop: 'tenantName', placeholder: '请输入租户名称' }]
const columns = [
  { title: '租户名称', dataIndex: 'tenantName', key: 'tenantName' },
  { title: '编码', dataIndex: 'tenantCode', key: 'tenantCode' },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName' },
  { title: '电话', dataIndex: 'contactPhone', key: 'contactPhone' },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 130 },
]
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const records = ref<TenantVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({ tenantName: '', tenantCode: '', contactName: '', contactPhone: '', status: 1 })

async function loadData() {
  loading.value = true
  try {
    const data = await getTenantPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      tenantName: (searchModel.tenantName as string) || undefined,
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
  Object.keys(searchModel).forEach((k) => (searchModel[k] = undefined))
  pageNum.value = 1
  loadData()
}
function openCreate() {
  editingId.value = undefined
  Object.assign(form, { tenantName: '', tenantCode: '', contactName: '', contactPhone: '', status: 1 })
  modalOpen.value = true
}
function openEdit(record: TenantVo) {
  editingId.value = record.id
  Object.assign(form, {
    tenantName: record.tenantName,
    tenantCode: record.tenantCode,
    contactName: record.contactName || '',
    contactPhone: record.contactPhone || '',
    status: record.status,
  })
  modalOpen.value = true
}
async function onSubmit() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateTenant({ ...form, id: editingId.value })
    } else {
      await createTenant(form)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}
function onDelete(record: TenantVo) {
  Modal.confirm({
    title: '确认删除租户',
    content: `确定删除 ${record.tenantName} 吗？`,
    onOk: async () => {
      await deleteTenant(record.id)
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

