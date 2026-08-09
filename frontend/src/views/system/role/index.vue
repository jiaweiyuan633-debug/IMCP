<template>
  <a-card title="角色管理">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:role:add'" type="primary" @click="openCreate">新增角色</a-button>
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
        <template v-else-if="column.key === 'dataScope'">
          {{ dataScopeText(record.dataScope) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:role:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:role:assign'" @click="openAssign(record)">分配菜单</a>
            <a v-permission="'system:role:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑角色' : '新增角色'"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="角色编码" required>
          <a-input v-model:value="form.code" />
        </a-form-item>
        <a-form-item label="角色名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sort" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item label="数据权限">
          <a-select v-model:value="form.dataScope" :options="dataScopeOptions" />
        </a-form-item>
        <a-form-item v-if="form.dataScope === 2" label="授权部门">
          <a-tree
            v-model:checked-keys="checkedDeptKeys"
            :tree-data="deptTreeData"
            checkable
            default-expand-all
            :field-names="{ label: 'deptName', value: 'id', children: 'children' }"
          />
        </a-form-item>
      </a-form>
    </ModalForm>

    <ModalForm
      v-model:open="assignOpen"
      title="分配菜单权限"
      :loading="saving"
      width="480"
      @ok="onAssignSubmit"
    >
      <a-tree
        v-model:checked-keys="checkedMenuKeys"
        :tree-data="menuTreeData"
        checkable
        default-expand-all
      />
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  getMenuTree,
  getRolePage,
  getDeptTree,
  updateRole,
} from '@/api/system'
import type { RoleSaveRequest } from '@/api/system'
import type { DeptVo, MenuNode, RoleVo, SearchField } from '@/types'

const searchFields: SearchField[] = [
  { label: '角色编码', prop: 'code', placeholder: '请输入角色编码' },
  { label: '角色名称', prop: 'name', placeholder: '请输入角色名称' },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '禁用', value: 0 },
    ],
  },
]

const columns = [
  { title: '角色编码', dataIndex: 'code', key: 'code' },
  { title: '角色名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status', width: 90 },
  { title: '数据权限', key: 'dataScope', width: 110 },
  { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
  { title: '操作', key: 'actions', width: 180 },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const dataScopeOptions = [
  { label: '全部数据', value: 1 },
  { label: '自定义部门', value: 2 },
  { label: '本部门', value: 3 },
  { label: '本部门及以下', value: 4 },
  { label: '仅本人', value: 5 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const records = ref<RoleVo[]>([])
const modalOpen = ref(false)
const assignOpen = ref(false)
const editingId = ref<number | undefined>()
const currentAssignRole = ref<RoleVo | null>(null)
const checkedMenuKeys = ref<number[]>([])
const menuTreeData = ref<MenuNode[]>([])
const deptTreeData = ref<DeptVo[]>([])
const checkedDeptKeys = ref<number[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({
  code: '',
  name: '',
  description: '',
  status: 1,
  dataScope: 1,
  sort: 0,
})

async function loadData() {
  loading.value = true
  try {
    const data = await getRolePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      code: (searchModel.code as string) || undefined,
      name: (searchModel.name as string) || undefined,
      status: searchModel.status as number | undefined,
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
  Object.assign(form, { code: '', name: '', description: '', status: 1, dataScope: 1, sort: 0 })
  checkedDeptKeys.value = []
  modalOpen.value = true
}

function openEdit(record: RoleVo) {
  editingId.value = record.id
  Object.assign(form, {
    code: record.code,
    name: record.name,
    description: record.description || '',
    status: record.status,
    dataScope: record.dataScope,
    sort: record.sort,
  })
  checkedDeptKeys.value = record.deptIds || []
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.code || !form.name) {
    message.warning('请填写角色编码和名称')
    return
  }
  saving.value = true
  try {
    const payload: RoleSaveRequest = {
      code: form.code,
      name: form.name,
      description: form.description,
      status: form.status,
      dataScope: form.dataScope,
      sort: form.sort,
      menuIds: [],
      deptIds: checkedDeptKeys.value,
    }
    if (editingId.value) {
      payload.id = editingId.value
      await updateRole(payload)
    } else {
      await createRole(payload)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function openAssign(record: RoleVo) {
  currentAssignRole.value = record
  checkedMenuKeys.value = record.menuIds || []
  assignOpen.value = true
}

function onAssignSubmit() {
  if (!currentAssignRole.value) {
    return
  }
  saving.value = true
  assignRoleMenus(currentAssignRole.value.id, checkedMenuKeys.value)
    .then(() => {
      message.success('分配成功')
      assignOpen.value = false
      loadData()
    })
    .finally(() => {
      saving.value = false
    })
}

function onDelete(record: RoleVo) {
  Modal.confirm({
    title: '确认删除角色',
    content: `确定删除角色 ${record.name} 吗？`,
    onOk: async () => {
      await deleteRole(record.id)
      message.success('删除成功')
      loadData()
    },
  })
}

onMounted(async () => {
  menuTreeData.value = await getMenuTree()
  deptTreeData.value = await getDeptTree()
  loadData()
})

function dataScopeText(scope: number) {
  const map: Record<number, string> = {
    1: '全部数据',
    2: '自定义部门',
    3: '本部门',
    4: '本部门及以下',
    5: '仅本人',
  }
  return map[scope] || String(scope)
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

