<template>
  <a-card title="部门管理">
    <div class="toolbar">
      <a-button v-permission="'system:dept:add'" type="primary" @click="openCreate()">新增部门</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="tree"
      :loading="loading"
      row-key="id"
      children-column-name="children"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:dept:add'" @click="openCreate(record)">新增下级</a>
            <a v-permission="'system:dept:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:dept:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑部门' : '新增部门'"
      :loading="saving"
      width="520"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="上级部门">
          <a-tree-select
            v-model:value="form.parentId"
            :tree-data="parentTreeData"
            tree-default-expand-all
            :field-names="{ label: 'deptName', value: 'id', children: 'children' }"
          />
        </a-form-item>
        <a-form-item label="部门名称" required>
          <a-input v-model:value="form.deptName" />
        </a-form-item>
        <a-form-item label="负责人">
          <a-input v-model:value="form.leader" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="form.phone" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="form.email" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.orderNum" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { createDept, deleteDept, getDeptTree, updateDept } from '@/api/system'
import type { DeptSaveRequest, DeptVo } from '@/api/system'

const columns = [
  { title: '部门名称', dataIndex: 'deptName', key: 'deptName' },
  { title: '负责人', dataIndex: 'leader', key: 'leader' },
  { title: '联系电话', dataIndex: 'phone', key: 'phone' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '排序', dataIndex: 'orderNum', key: 'orderNum', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 180 },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const tree = ref<DeptVo[]>([])
const form = reactive({
  parentId: 0,
  deptName: '',
  leader: '',
  phone: '',
  email: '',
  orderNum: 0,
  status: 1,
})

const parentTreeData = computed<DeptVo[]>(() => [
  { id: 0, parentId: 0, deptName: '根部门', orderNum: 0, status: 1, children: tree.value },
])

async function loadData() {
  loading.value = true
  try {
    tree.value = await getDeptTree()
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: DeptVo) {
  editingId.value = undefined
  Object.assign(form, {
    parentId: parent ? parent.id : 0,
    deptName: '',
    leader: '',
    phone: '',
    email: '',
    orderNum: 0,
    status: 1,
  })
  modalOpen.value = true
}

function openEdit(record: DeptVo) {
  editingId.value = record.id
  Object.assign(form, {
    parentId: record.parentId,
    deptName: record.deptName,
    leader: record.leader || '',
    phone: record.phone || '',
    email: record.email || '',
    orderNum: record.orderNum,
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.deptName) {
    message.warning('请填写部门名称')
    return
  }
  saving.value = true
  try {
    const payload: DeptSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateDept(payload)
    } else {
      await createDept(payload)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: DeptVo) {
  Modal.confirm({
    title: '确认删除部门',
    content: `确定删除部门 ${record.deptName} 吗？`,
    onOk: async () => {
      await deleteDept(record.id)
      message.success('删除成功')
      loadData()
    },
  })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

