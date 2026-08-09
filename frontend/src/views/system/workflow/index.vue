<template>
  <a-card title="工作流">
    <div class="toolbar">
      <a-button type="primary" @click="openCreate">发起流程</a-button>
    </div>
    <a-table :columns="columns" :data-source="records" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space v-if="record.status === 'PENDING'">
            <a v-permission="'system:workflow:approve'" @click="onApprove(record)">通过</a>
            <a v-permission="'system:workflow:reject'" @click="onReject(record)">拒绝</a>
          </a-space>
        </template>
      </template>
    </a-table>
    <ModalForm v-model:open="modalOpen" title="发起流程" :loading="saving" @ok="onSubmit">
      <a-form layout="vertical" :model="form">
        <a-form-item label="流程名称" required><a-input v-model:value="form.processName" /></a-form-item>
        <a-form-item label="业务类型"><a-input v-model:value="form.bizType" /></a-form-item>
        <a-form-item label="内容"><a-textarea v-model:value="form.content" :rows="4" /></a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { approveWorkflow, createWorkflow, getWorkflowPage, rejectWorkflow } from '@/api/system'
import type { WorkflowVo } from '@/api/system'

const columns = [
  { title: '流程名称', dataIndex: 'processName', key: 'processName' },
  { title: '申请人', dataIndex: 'applicantName', key: 'applicantName' },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: '状态', key: 'status', width: 100 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'actions', width: 130 },
]
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const records = ref<WorkflowVo[]>([])
const form = reactive({ processName: '', bizType: 'demo', content: '' })

async function loadData() {
  loading.value = true
  try {
    const data = await getWorkflowPage({ pageNum: 1, pageSize: 100 })
    records.value = data.records
  } finally {
    loading.value = false
  }
}
function openCreate() {
  Object.assign(form, { processName: '', bizType: 'demo', content: '' })
  modalOpen.value = true
}
async function onSubmit() {
  saving.value = true
  try {
    await createWorkflow(form)
    message.success('已发起')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}
function onApprove(record: WorkflowVo) {
  Modal.confirm({
    title: '确认通过',
    content: `通过 ${record.processName}？`,
    onOk: async () => {
      await approveWorkflow(record.id)
      message.success('已通过')
      loadData()
    },
  })
}
function onReject(record: WorkflowVo) {
  Modal.confirm({
    title: '确认拒绝',
    content: `拒绝 ${record.processName}？`,
    onOk: async () => {
      await rejectWorkflow(record.id)
      message.success('已拒绝')
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

