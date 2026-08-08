<template>
  <a-card title="用户管理">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:user:add'" type="primary" @click="openCreate">新增用户</a-button>
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
          <a-switch
            :checked="record.status === 1"
            @change="(checked: boolean) => toggleStatus(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'roleNames'">
          {{ (record.roleNames || []).join(', ') || '-' }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:user:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:user:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑用户' : '新增用户'"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" />
        </a-form-item>
        <a-form-item :label="editingId ? '密码（留空不修改）' : '密码'" required>
          <a-input-password v-model:value="form.password" />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="form.nickname" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="form.email" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input v-model:value="form.phone" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="form.roleIds"
            mode="multiple"
            :options="roleOptions"
            option-filter-prop="label"
          />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import {
  createUser,
  deleteUser,
  getRoleOptions,
  getUserPage,
  updateUser,
  updateUserStatus,
} from '@/api/system'
import type { UserSaveRequest } from '@/api/system'
import type { RoleOptionVo, SearchField, UserVo } from '@/types'

const searchFields: SearchField[] = [
  { label: '用户名', prop: 'username', placeholder: '请输入用户名' },
  { label: '昵称', prop: 'nickname', placeholder: '请输入昵称' },
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
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
  { title: '角色', key: 'roleNames' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '手机号', dataIndex: 'phone', key: 'phone' },
  { title: '状态', key: 'status', width: 90 },
  { title: '最近登录', dataIndex: 'lastLoginTime', key: 'lastLoginTime' },
  { title: '操作', key: 'actions', width: 120 },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const records = ref<UserVo[]>([])
const roleOptions = ref<RoleOptionVo[]>([])
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1,
  roleIds: [] as number[],
})

async function loadData() {
  loading.value = true
  try {
    const data = await getUserPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      username: (searchModel.username as string) || undefined,
      nickname: (searchModel.nickname as string) || undefined,
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
  pageNum.value = 1
  loadData()
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: 1,
    roleIds: [],
  })
  modalOpen.value = true
}

function openEdit(record: UserVo) {
  editingId.value = record.id
  Object.assign(form, {
    username: record.username,
    password: '',
    nickname: record.nickname || '',
    email: record.email || '',
    phone: record.phone || '',
    status: record.status,
    roleIds: record.roleIds || [],
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.username || (!editingId.value && !form.password)) {
    message.warning('请填写用户名和密码')
    return
  }
  saving.value = true
  try {
    const payload: UserSaveRequest = {
      username: form.username,
      password: form.password || undefined,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      status: form.status,
      roleIds: form.roleIds,
    }
    if (editingId.value) {
      payload.id = editingId.value
      await updateUser(payload)
    } else {
      await createUser(payload)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(record: UserVo, checked: boolean) {
  await updateUserStatus(record.id, checked ? 1 : 0)
  message.success('状态已更新')
  loadData()
}

function onDelete(record: UserVo) {
  Modal.confirm({
    title: '确认删除用户',
    content: `确定删除用户 ${record.username} 吗？`,
    onOk: async () => {
      await deleteUser(record.id)
      message.success('删除成功')
      loadData()
    },
  })
}

onMounted(async () => {
  roleOptions.value = await getRoleOptions()
  loadData()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

