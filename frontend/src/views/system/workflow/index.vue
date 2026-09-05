<template>
  <a-card :title="t('page.workflowTitle')">
    <a-tabs v-model:active-key="activeKey">
      <a-tab-pane key="instances" :tab="t('page.workflowInstances')">
        <ProSearchForm
          :fields="instanceSearchFields"
          :loading="instanceLoading"
          @search="onInstanceSearch"
          @reset="onInstanceReset"
        />
        <div class="toolbar">
          <a-button type="primary" @click="openWorkflowCreate">{{ t('page.workflowStart') }}</a-button>
        </div>
        <ProTable
          v-model:page-num="instancePageNum"
          v-model:page-size="instancePageSize"
          :columns="instanceColumns"
          :data-source="instanceRecords"
          :loading="instanceLoading"
          :total="instanceTotal"
          :error="instanceError"
          row-key="id"
          @change="loadInstances"
          @retry="loadInstances"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <StatusTag :value="record.status" />
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="openDetail(record)">{{ t('page.workflowDetail') }}</a>
                <a @click="openLogs(record)">{{ t('page.workflowLogs') }}</a>
                <a v-if="record.status === 'PENDING'" v-permission="'system:workflow:approve'" @click="openApprove(record)">{{ t('page.workflowApprove') }}</a>
                <a v-if="record.status === 'PENDING'" v-permission="'system:workflow:reject'" @click="openReject(record)">{{ t('page.workflowReject') }}</a>
                <a v-if="record.status === 'PENDING'" @click="openDelegate(record)">{{ t('page.workflowDelegate') }}</a>
                <a v-if="record.status === 'PENDING'" @click="onWithdraw(record)">{{ t('page.workflowWithdraw') }}</a>
              </a-space>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>

      <a-tab-pane key="tasks" :tab="t('page.workflowTasks')">
        <ProSearchForm
          :fields="taskSearchFields"
          :loading="taskLoading"
          @search="onTaskSearch"
          @reset="onTaskReset"
        />
        <ProTable
          v-model:page-num="taskPageNum"
          v-model:page-size="taskPageSize"
          :columns="taskColumns"
          :data-source="taskRecords"
          :loading="taskLoading"
          :total="taskTotal"
          :error="taskError"
          row-key="id"
          @change="loadTasks"
          @retry="loadTasks"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <StatusTag :value="record.status" />
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="openDetail(record)">{{ t('page.workflowDetail') }}</a>
                <a @click="openLogs(record)">{{ t('page.workflowLogs') }}</a>
                <a v-permission="'system:workflow:approve'" @click="openApprove(record)">{{ t('page.workflowApprove') }}</a>
                <a v-permission="'system:workflow:reject'" @click="openReject(record)">{{ t('page.workflowReject') }}</a>
                <a @click="openDelegate(record)">{{ t('page.workflowDelegate') }}</a>
              </a-space>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>

      <a-tab-pane key="defs" :tab="t('page.workflowDefs')">
        <div class="toolbar">
          <a-button v-permission="'system:workflow:def:add'" type="primary" @click="openDefCreate">{{ t('page.workflowDefAdd') }}</a-button>
        </div>
        <ProTable
          v-model:page-num="defPageNum"
          v-model:page-size="defPageSize"
          :columns="defColumns"
          :data-source="defRecords"
          :loading="defLoading"
          :total="defTotal"
          :error="defError"
          row-key="id"
          @change="loadDefs"
          @retry="loadDefs"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 1 ? 'green' : 'default'">
                {{ record.status === 1 ? t('common.enabled') : t('common.disabled') }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="openDefNodes(record)">{{ t('page.workflowDefNodes') }}</a>
                <a v-if="record.status === 0" v-permission="'system:workflow:def:edit'" @click="onPublish(record)">{{ t('page.workflowPublish') }}</a>
                <a v-if="record.status === 1" v-permission="'system:workflow:def:edit'" @click="onUnpublish(record)">{{ t('page.workflowUnpublish') }}</a>
                <a v-permission="'system:workflow:def:edit'" @click="openDefEdit(record)">{{ t('common.edit') }}</a>
                <a v-permission="'system:workflow:def:delete'" @click="onDefDelete(record)">{{ t('common.delete') }}</a>
              </a-space>
            </template>
          </template>
        </ProTable>
      </a-tab-pane>
    </a-tabs>

    <ModalForm v-model:open="workflowModalOpen" :title="t('page.workflowStart')" :loading="workflowSaving" @ok="onWorkflowSubmit">
      <a-form layout="vertical" :model="workflowForm">
        <a-form-item :label="t('page.workflowDefName')" required>
          <a-select v-model:value="workflowForm.processDefId" :options="processDefOptions" option-label-prop="defName" />
        </a-form-item>
        <a-form-item :label="t('page.workflowName')" required>
          <a-input v-model:value="workflowForm.processName" />
        </a-form-item>
        <a-form-item :label="t('page.workflowBizType')">
          <a-input v-model:value="workflowForm.bizType" />
        </a-form-item>
        <a-form-item :label="t('page.workflowContent')">
          <a-textarea v-model:value="workflowForm.content" :rows="4" />
        </a-form-item>
        <a-form-item :label="t('page.workflowFormData')">
          <a-textarea v-model:value="workflowForm.formData" :rows="3" placeholder='{"amount": 1000}' />
        </a-form-item>
      </a-form>
    </ModalForm>

    <ModalForm
      v-model:open="defModalOpen"
      :title="defEditingId ? t('page.workflowDefEdit') : t('page.workflowDefAdd')"
      :loading="defSaving"
      width="720"
      @ok="onDefSubmit"
    >
      <a-form layout="vertical" :model="defForm">
        <a-form-item :label="t('page.workflowDefName')" required>
          <a-input v-model:value="defForm.defName" />
        </a-form-item>
        <a-form-item :label="t('page.workflowDefKey')" required>
          <a-input v-model:value="defForm.defKey" placeholder="general_approval" />
        </a-form-item>
        <a-form-item :label="t('page.workflowDefDescription')">
          <a-textarea v-model:value="defForm.description" :rows="2" />
        </a-form-item>
        <a-form-item :label="t('page.workflowDefStatus')">
          <a-switch v-model:checked="defForm.status" :checked-value="1" :un-checked-value="0" />
        </a-form-item>
        <a-divider>{{ t('page.workflowDefNodes') }}</a-divider>
        <div v-for="(node, index) in defForm.nodes" :key="index" class="node-row">
          <a-input v-model:value="node.nodeName" :placeholder="t('page.workflowNodeName')" />
          <a-input v-model:value="node.nodeKey" :placeholder="t('page.workflowNodeKey')" />
          <a-select v-model:value="node.nodeType" :options="nodeTypeOptions" :placeholder="t('page.workflowNodeType')" />
          <a-input v-model:value="node.conditionExpression" placeholder="#amount > 1000" />
          <a-input-number v-model:value="node.timeoutHours" :min="1" :placeholder="t('page.workflowNodeTimeout')" style="width: 100%" />
          <a-select v-model:value="node.approverRoleId" :options="roleOptions" allow-clear :placeholder="t('page.workflowNodeRole')" style="width: 160px" />
          <a-button type="text" danger @click="removeNode(index)">
            <DeleteOutlined />
          </a-button>
        </div>
        <a-button type="dashed" block @click="addNode">{{ t('page.workflowAddNode') }}</a-button>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="approvalModalOpen" :title="approvalAction === 'approve' ? t('page.workflowApprovalTitle') : t('page.workflowRejectTitle')" :confirm-loading="approvalSaving" @ok="onApprovalSubmit">
      <a-form layout="vertical">
        <a-form-item v-if="approvalAction === 'approve' && currentNodeOptions.length > 1" :label="t('page.workflowNodeName')" required>
          <a-select v-model:value="selectedNodeId" :options="currentNodeOptions" />
        </a-form-item>
        <a-form-item :label="t('page.workflowApprovalRemark')">
          <a-textarea v-model:value="approvalRemark" :rows="3" :placeholder="approvalAction === 'approve' ? t('page.workflowApprovePlaceholder') : t('page.workflowRejectPlaceholder')" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="delegateModalOpen" :title="t('page.workflowDelegate')" :confirm-loading="delegateSaving" @ok="onDelegateSubmit">
      <a-form layout="vertical">
        <a-form-item :label="t('page.workflowDelegateTo')" required>
          <a-select v-model:value="delegateUserId" :options="userOptions" show-search option-filter-prop="label" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="logsModalOpen" :title="t('page.workflowLogs')" :footer="null" width="720">
      <a-table :columns="logColumns" :data-source="logRecords" :loading="logLoading" row-key="id" :pagination="false" size="small" />
    </a-modal>

    <a-modal v-model:open="nodesModalOpen" :title="`${t('page.workflowDefNodes')}: ${activeDef?.defName || ''}`" :footer="null" width="620">
      <a-table :columns="nodeViewColumns" :data-source="nodeViewRecords" :pagination="false" size="small" />
    </a-modal>

    <a-drawer v-model:open="detailOpen" :title="t('page.workflowDetail')" width="560">
      <a-spin :spinning="detailLoading" tip="">
      <template v-if="detail">
        <a-descriptions :column="1" size="small" bordered :title="t('page.workflowDetailBase')">
          <a-descriptions-item :label="t('page.workflowName')">{{ detail.processName }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowBizType')">{{ detail.bizType || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowBizId')">{{ detail.bizId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowApplicant')">{{ detail.applicantName || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowStatus')"><StatusTag :value="detail.status" /></a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowCurrentNode')">{{ detail.currentNodeName || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowFlowInstance')">{{ detail.flowInstanceId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowContent')">{{ detail.content || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowApprovalRemark')">{{ detail.remark || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.workflowCreatedAt')">{{ formatDateTime(detail.createdAt) }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions :column="1" size="small" bordered :title="t('page.workflowFormDataTitle')" class="detail-section">
          <a-descriptions-item v-if="!formDataEntries.length" :label="t('page.workflowNoFormData')">-</a-descriptions-item>
          <a-descriptions-item v-for="entry in formDataEntries" :key="entry.key" :label="entry.key">
            {{ entry.value }}
          </a-descriptions-item>
        </a-descriptions>

        <a-divider>{{ t('page.workflowTraceTitle') }}</a-divider>
        <a-empty v-if="!detail.trace?.length" :description="t('page.workflowNoTrace')" />
        <a-timeline v-else>
          <a-timeline-item v-for="(item, index) in detail.trace" :key="index">
            <div class="trace-title">
              <span class="trace-node">{{ item.nodeName || item.nodeCode || '-' }}</span>
              <StatusTag :value="item.flowStatus || ''" />
            </div>
            <div v-if="item.approver" class="trace-meta">{{ t('page.workflowTraceApprover') }}: {{ item.approver }}</div>
            <div v-if="item.message" class="trace-meta">{{ t('page.workflowTraceMessage') }}: {{ item.message }}</div>
            <div v-if="item.createTime" class="trace-meta">{{ t('page.workflowTraceTime') }}: {{ formatDateTime(item.createTime) }}</div>
          </a-timeline-item>
        </a-timeline>
      </template>
      </a-spin>
    </a-drawer>
  </a-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined } from '@ant-design/icons-vue'
import ModalForm from '@/components/ModalForm.vue'
import ProTable from '@/components/ProTable.vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import {
  approveWorkflow,
  createProcessDef,
  createWorkflow,
  delegateWorkflow,
  deleteProcessDef,
  getProcessDefNodes,
  getProcessDefOptions,
  getProcessDefPage,
  getRoleOptions,
  getUserPage,
  getWorkflowDetail,
  getWorkflowLogs,
  getWorkflowCurrentNodes,
  getWorkflowPage,
  getWorkflowTasks,
  publishProcessDef,
  rejectWorkflow,
  unpublishProcessDef,
  updateProcessDef,
  withdrawWorkflow,
} from '@/api/system'
import type { ProcessDefVo, ProcessNodeVo, WorkflowDetailVo, WorkflowLogVo, WorkflowVo } from '@/api/system'
import type { RoleOptionVo } from '@/types'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn, formatDateTime } from '@/utils/table'

const { t } = useI18n()
const route = useRoute()

const activeKey = ref('instances')

const instanceColumns = [
  { title: t('page.workflowName'), dataIndex: 'processName', key: 'processName' },
  { title: t('page.workflowApplicant'), dataIndex: 'applicantName', key: 'applicantName' },
  { title: t('page.workflowCurrentNode'), dataIndex: 'currentNodeName', key: 'currentNodeName' },
  { title: t('page.workflowStatus'), key: 'status', width: 100 },
  dateColumn('createdAt', { title: t('page.workflowCreatedAt'), width: 170 }),
  { title: t('page.workflowActions'), key: 'actions', width: 170 },
]

const taskColumns = [
  { title: t('page.workflowName'), dataIndex: 'processName', key: 'processName' },
  { title: t('page.workflowApplicant'), dataIndex: 'applicantName', key: 'applicantName' },
  { title: t('page.workflowCurrentNode'), dataIndex: 'currentNodeName', key: 'currentNodeName' },
  { title: t('page.workflowContent'), dataIndex: 'content', key: 'content', ellipsis: true },
  { title: t('page.workflowActions'), key: 'actions', width: 160 },
]

const defColumns = [
  { title: t('page.workflowDefName'), dataIndex: 'defName', key: 'defName' },
  { title: t('page.workflowDefKey'), dataIndex: 'defKey', key: 'defKey' },
  { title: t('page.workflowDefDescription'), dataIndex: 'description', key: 'description' },
  { title: t('page.workflowDefStatus'), key: 'status', width: 90 },
  { title: t('page.workflowActions'), key: 'actions', width: 180 },
]

const logColumns = [
  { title: t('page.monitorAction'), dataIndex: 'action', key: 'action', width: 110 },
  { title: t('page.workflowOperator'), dataIndex: 'operatorName', key: 'operatorName', width: 130 },
  { title: t('page.workflowApprovalRemark'), dataIndex: 'remark', key: 'remark' },
  dateColumn('createdAt', { title: t('page.workflowCreatedAt'), width: 170 }),
]

const nodeViewColumns = [
  { title: t('page.workflowNodeOrder'), dataIndex: 'nodeOrder', key: 'nodeOrder', width: 70 },
  { title: t('page.workflowNodeName'), dataIndex: 'nodeName', key: 'nodeName' },
  { title: t('page.workflowNodeKey'), dataIndex: 'nodeKey', key: 'nodeKey' },
  { title: t('page.workflowNodeRole'), dataIndex: 'roleName', key: 'roleName' },
]

const {
  pageNum: instancePageNum,
  pageSize: instancePageSize,
  total: instanceTotal,
  loading: instanceLoading,
  records: instanceRecords,
  error: instanceError,
  loadData: loadInstances,
  onSearch: onInstanceSearch,
  onReset: onInstanceReset,
} = useTableQuery<WorkflowVo>(getWorkflowPage, {
  buildParams: (query) => ({
    processName: (query.processName as string) || undefined,
    status: query.status as string | undefined,
    bizType: query.bizType as string | undefined,
  }),
})

const instanceSearchFields: SearchField[] = [
  { label: t('page.workflowName'), prop: 'processName' },
  {
    label: t('page.workflowStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.pending'), value: 'PENDING' },
      { label: t('common.approved'), value: 'APPROVED' },
      { label: t('common.rejected'), value: 'REJECTED' },
      { label: t('common.withdrawn'), value: 'WITHDRAWN' },
    ],
  },
  { label: t('page.workflowBizType'), prop: 'bizType' },
]

const {
  pageNum: taskPageNum,
  pageSize: taskPageSize,
  total: taskTotal,
  loading: taskLoading,
  records: taskRecords,
  error: taskError,
  loadData: loadTasks,
  onSearch: onTaskSearch,
  onReset: onTaskReset,
} = useTableQuery<WorkflowVo>(getWorkflowTasks, {
  buildParams: (query) => ({
    processName: (query.processName as string) || undefined,
  }),
})

const taskSearchFields: SearchField[] = [
  { label: t('page.workflowName'), prop: 'processName' },
]

const {
  pageNum: defPageNum,
  pageSize: defPageSize,
  total: defTotal,
  loading: defLoading,
  records: defRecords,
  error: defError,
  loadData: loadDefs,
} = useTableQuery<ProcessDefVo>(getProcessDefPage)

const processDefOptions = ref<ProcessDefVo[]>([])
const roleOptions = ref<RoleOptionVo[]>([])
const userOptions = ref<{ label: string; value: number }[]>([])
const nodeTypeOptions = [
  { label: t('page.workflowNodeApprove'), value: 'APPROVE' },
  { label: t('page.workflowNodeCondition'), value: 'CONDITION' },
]

const workflowModalOpen = ref(false)
const workflowSaving = ref(false)
const workflowForm = reactive({
  processDefId: undefined as number | undefined,
  processName: '',
  bizType: 'demo',
  content: '',
  formData: '',
})

const defModalOpen = ref(false)
const defSaving = ref(false)
const defEditingId = ref<number | undefined>()
const defForm = reactive({
  defName: '',
  defKey: '',
  description: '',
  status: 1,
  nodes: [] as ProcessNodeVo[],
})

const approvalModalOpen = ref(false)
const approvalSaving = ref(false)
const approvalAction = ref<'approve' | 'reject'>('approve')
const approvalRemark = ref('')
const approvalTarget = ref<WorkflowVo | null>(null)
const currentNodeOptions = ref<{ label: string; value: number }[]>([])
const selectedNodeId = ref<number | undefined>()
const delegateModalOpen = ref(false)
const delegateSaving = ref(false)
const delegateUserId = ref<number | undefined>()
const delegateTarget = ref<WorkflowVo | null>(null)

const logsModalOpen = ref(false)
const logLoading = ref(false)
const logRecords = ref<WorkflowLogVo[]>([])

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<WorkflowDetailVo | null>(null)
const formDataEntries = computed(() =>
  Object.entries(detail.value?.formData ?? {}).map(([key, value]) => ({
    key,
    value: typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value),
  })),
)

const nodesModalOpen = ref(false)
const activeDef = ref<ProcessDefVo | null>(null)
const nodeViewRecords = ref<Array<ProcessNodeVo & { roleName?: string }>>([])

async function loadOptions() {
  processDefOptions.value = await getProcessDefOptions()
  roleOptions.value = await getRoleOptions()
  const users = await getUserPage({ pageNum: 1, pageSize: 100 })
  userOptions.value = users.records.map((user) => ({ label: `${user.username}${user.nickname ? ` (${user.nickname})` : ''}`, value: user.id }))
}

function openWorkflowCreate() {
  Object.assign(workflowForm, {
    processDefId: processDefOptions.value[0]?.id,
    processName: '',
    bizType: 'demo',
    content: '',
    formData: '',
  })
  workflowModalOpen.value = true
}

async function onWorkflowSubmit() {
  if (!workflowForm.processDefId || !workflowForm.processName) {
    message.warning(t('page.workflowSelectDef'))
    return
  }
  workflowSaving.value = true
  try {
    await createWorkflow(workflowForm)
    message.success(t('page.workflowStarted'))
    workflowModalOpen.value = false
    loadInstances()
    loadTasks()
  } finally {
    workflowSaving.value = false
  }
}

function openDefCreate() {
  defEditingId.value = undefined
  Object.assign(defForm, { defName: '', defKey: '', description: '', status: 1 })
  defForm.nodes = [emptyNode(0)]
  defModalOpen.value = true
}

async function openDefEdit(record: ProcessDefVo) {
  defEditingId.value = record.id
  Object.assign(defForm, {
    defName: record.defName,
    defKey: record.defKey,
    description: record.description || '',
    status: record.status,
  })
  const nodes = await getProcessDefNodes(record.id)
  defForm.nodes = nodes.length ? nodes.map(normalizeNode) : [emptyNode(0)]
  defModalOpen.value = true
}

function addNode() {
  defForm.nodes.push(emptyNode(defForm.nodes.length))
}

function emptyNode(order: number): ProcessNodeVo {
  return {
    nodeName: '',
    nodeKey: '',
    nodeType: 'APPROVE',
    conditionExpression: '',
    timeoutHours: 48,
    nodeOrder: order,
    approverRoleId: undefined,
  }
}

function normalizeNode(node: ProcessNodeVo): ProcessNodeVo {
  return {
    ...node,
    nodeType: node.nodeType || 'APPROVE',
    conditionExpression: node.conditionExpression || '',
    timeoutHours: node.timeoutHours || 48,
  }
}

function removeNode(index: number) {
  defForm.nodes.splice(index, 1)
}

async function onDefSubmit() {
  if (!defForm.defName || !defForm.defKey || defForm.nodes.length === 0) {
    message.warning(t('page.workflowDefRequired'))
    return
  }
  defSaving.value = true
  try {
    const payload = {
      ...defForm,
      nodes: defForm.nodes.map((node, index) => ({ ...node, nodeOrder: index })),
    }
    if (defEditingId.value) {
      await updateProcessDef({ ...payload, id: defEditingId.value })
    } else {
      await createProcessDef(payload)
    }
    message.success(t('page.workflowDefSaved'))
    defModalOpen.value = false
    loadDefs()
    loadOptions()
  } finally {
    defSaving.value = false
  }
}

function onDefDelete(record: ProcessDefVo) {
  Modal.confirm({
    title: t('page.workflowDefDeleteTitle'),
    content: t('page.workflowDefDeleteConfirm', { name: record.defName }),
    onOk: async () => {
      await deleteProcessDef(record.id)
      message.success(t('page.workflowDefDeleted'))
      loadDefs()
    },
  })
}

async function onPublish(record: ProcessDefVo) {
  await publishProcessDef(record.id)
  message.success(t('page.workflowOperationSuccess'))
  loadDefs()
  loadOptions()
}

async function onUnpublish(record: ProcessDefVo) {
  await unpublishProcessDef(record.id)
  message.success(t('page.workflowOperationSuccess'))
  loadDefs()
  loadOptions()
}

async function openApprove(record: WorkflowVo) {
  approvalAction.value = 'approve'
  approvalRemark.value = ''
  approvalTarget.value = record
  const nodes = await getWorkflowCurrentNodes(record.id)
  currentNodeOptions.value = nodes.map((node) => ({ label: node.nodeName, value: node.taskId ?? node.id as number }))
  selectedNodeId.value = record.currentTaskId
    ?? nodes.find((node) => node.taskId === record.currentTaskId)?.taskId
    ?? currentNodeOptions.value[0]?.value
  approvalModalOpen.value = true
}

function openReject(record: WorkflowVo) {
  approvalAction.value = 'reject'
  approvalRemark.value = ''
  approvalTarget.value = record
  // 驳回复用 selectedNodeId 存待办 taskId，精确定位当前待办（多待办场景）
  selectedNodeId.value = record.currentTaskId
  approvalModalOpen.value = true
}

async function onApprovalSubmit() {
  if (!approvalTarget.value) {
    return
  }
  approvalSaving.value = true
  try {
    if (approvalAction.value === 'approve') {
      await approveWorkflow(approvalTarget.value.id, approvalRemark.value, selectedNodeId.value)
    } else {
      await rejectWorkflow(approvalTarget.value.id, approvalRemark.value, selectedNodeId.value)
    }
    message.success(t('page.workflowOperationSuccess'))
    approvalModalOpen.value = false
    loadInstances()
    loadTasks()
  } finally {
    approvalSaving.value = false
  }
}

function openDelegate(record: WorkflowVo) {
  delegateTarget.value = record
  delegateUserId.value = undefined
  delegateModalOpen.value = true
}

async function onDelegateSubmit() {
  if (!delegateTarget.value || !delegateUserId.value) {
    message.warning(`${t('common.inputPlaceholder')}${t('page.workflowDelegateTo')}`)
    return
  }
  delegateSaving.value = true
  try {
    await delegateWorkflow(delegateTarget.value.id, delegateUserId.value)
    message.success(t('page.workflowOperationSuccess'))
    delegateModalOpen.value = false
    loadInstances()
    loadTasks()
  } finally {
    delegateSaving.value = false
  }
}

function onWithdraw(record: WorkflowVo) {
  Modal.confirm({
    title: t('page.workflowWithdrawTitle'),
    content: t('page.workflowWithdrawConfirm', { name: record.processName }),
    onOk: async () => {
      await withdrawWorkflow(record.id)
      message.success(t('page.workflowOperationSuccess'))
      loadInstances()
      loadTasks()
    },
  })
}

async function openLogs(record: WorkflowVo) {
  logsModalOpen.value = true
  logLoading.value = true
  try {
    logRecords.value = await getWorkflowLogs(record.id)
  } finally {
    logLoading.value = false
  }
}

async function openDetail(record: WorkflowVo) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getWorkflowDetail(record.id)
  } finally {
    detailLoading.value = false
  }
}

async function openDefNodes(record: ProcessDefVo) {
  activeDef.value = record
  const nodes = await getProcessDefNodes(record.id)
  nodeViewRecords.value = nodes.map((node) => ({
    ...node,
    roleName: roleOptions.value.find((role) => role.id === node.approverRoleId)?.name || t('page.workflowAnyRole'),
  }))
  nodesModalOpen.value = true
}

loadOptions()

async function openFromQuery() {
  const id = Number(route.query.detail)
  if (!id) {
    return
  }
  activeKey.value = 'instances'
  await openDetail({ id } as WorkflowVo)
}

openFromQuery()
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.node-row {
  display: grid;
  grid-template-columns: 1fr 1fr 160px 32px;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-section {
  margin-top: 16px;
}

.trace-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.trace-node {
  font-weight: 500;
}

.trace-meta {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  line-height: 1.6;
}
</style>
