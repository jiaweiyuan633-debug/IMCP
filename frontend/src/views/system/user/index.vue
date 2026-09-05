<template>
  <a-card :title="t('page.userTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:user:add'" type="primary" @click="openCreate">{{ t('page.userAdd') }}</a-button>
      <a-button @click="onExport">{{ t('page.userExport') }}</a-button>
      <a-upload :show-upload-list="false" :before-upload="onImport">
        <a-button>{{ t('page.userImport') }}</a-button>
      </a-upload>
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
          <a-switch
            :checked="record.status === 1"
            @change="(checked: boolean) => toggleStatus(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'roleNames'">
          {{ (record.roleNames || []).join(', ') || '-' }}
        </template>
        <template v-else-if="column.key === 'deptName'">
          {{ record.deptName || '-' }}
        </template>
        <template v-else-if="column.key === 'postNames'">
          {{ (record.postNames || []).join(', ') || '-' }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:user:edit'" @click="openAssign(record)">{{ t('page.userAssignRole') }}</a>
            <a v-permission="'system:user:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:user:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.userEdit') : t('page.userAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.userAvatar')">
          <FileUpload v-model:value="form.avatar" />
        </a-form-item>
        <a-form-item :label="t('page.userUsername')" required>
          <a-input v-model:value="form.username" />
        </a-form-item>
        <a-form-item :label="t('page.userDept')">
          <a-tree-select
            v-model:value="form.deptId"
            :tree-data="deptTree"
            allow-clear
            tree-default-expand-all
            :field-names="{ label: 'deptName', value: 'id', children: 'children' }"
          />
        </a-form-item>
        <a-form-item
          :label="editingId ? t('page.userPasswordEditHint') : t('page.userPassword')"
          name="password"
          :rules="passwordRules"
          required
        >
          <a-input-password v-model:value="form.password" />
        </a-form-item>
        <a-form-item :label="t('page.userNickname')">
          <a-input v-model:value="form.nickname" />
        </a-form-item>
        <a-form-item :label="t('page.userEmail')">
          <a-input v-model:value="form.email" />
        </a-form-item>
        <a-form-item :label="t('page.userPhone')">
          <a-input v-model:value="form.phone" />
        </a-form-item>
        <a-form-item :label="t('page.userStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.userRole')">
          <a-select
            v-model:value="form.roleIds"
            mode="multiple"
            :options="roleOptions"
            option-filter-prop="label"
          />
        </a-form-item>
        <a-form-item :label="t('page.userPost')">
          <a-select
            v-model:value="form.postIds"
            mode="multiple"
            :options="postOptions"
            option-filter-prop="label"
          />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-modal v-model:open="assignOpen" :title="t('page.userAssignRoleTitle')" :confirm-loading="assignSaving" @ok="onAssignSubmit">
      <a-form layout="vertical">
        <a-form-item :label="t('page.userRole')">
          <a-select v-model:value="assignForm.roleIds" mode="multiple" :options="roleOptions" option-filter-prop="label" />
        </a-form-item>
        <a-form-item :label="t('page.userPost')">
          <a-select v-model:value="assignForm.postIds" mode="multiple" :options="postOptions" option-filter-prop="label" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import FileUpload from '@/components/FileUpload.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import {
  createUser,
  deleteUser,
  getDeptTree,
  getPostOptions,
  getRoleOptions,
  getUserPage,
  exportUsers,
  importUsers,
  assignUserRoles,
  assignUserPosts,
  updateUser,
  updateUserStatus,
} from '@/api/system'
import type { UserSaveRequest } from '@/api/system'
import type { DeptVo, PostOptionVo, RoleOptionVo, SearchField, UserVo } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'
import { isStrongPassword, PASSWORD_PATTERN } from '@/utils/validation'

const { t } = useI18n()

// 密码框行内即时校验（编辑时可空，仅当填写时按复杂度规则校验）
const passwordRules = [{ pattern: PASSWORD_PATTERN, message: t('page.passwordPolicy'), trigger: 'blur' }]

const searchFields: SearchField[] = [
  { label: t('page.userUsername'), prop: 'username', placeholder: `${t('common.inputPlaceholder')}${t('page.userUsername')}` },
  { label: t('page.userNickname'), prop: 'nickname', placeholder: `${t('common.inputPlaceholder')}${t('page.userNickname')}` },
  {
    label: t('page.userStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.userUsername'), dataIndex: 'username', key: 'username' },
  { title: t('page.userNickname'), dataIndex: 'nickname', key: 'nickname' },
  { title: t('page.userDept'), key: 'deptName' },
  { title: t('page.userPost'), key: 'postNames' },
  { title: t('page.userRole'), key: 'roleNames' },
  { title: t('page.userEmail'), dataIndex: 'email', key: 'email' },
  { title: t('page.userPhone'), dataIndex: 'phone', key: 'phone' },
  { title: t('page.userStatus'), key: 'status', width: 90 },
  dateColumn('lastLoginTime', { title: t('page.userLastLogin') }),
  { title: t('page.userActions'), key: 'actions', width: 120 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const roleOptions = ref<RoleOptionVo[]>([])
const postOptions = ref<PostOptionVo[]>([])
const deptTree = ref<DeptVo[]>([])
const modalOpen = ref(false)
const assignOpen = ref(false)
const assignSaving = ref(false)
const editingId = ref<number | undefined>()
const assignUserId = ref<number>()
const form = reactive({
  avatar: '',
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1,
  deptId: undefined as number | undefined,
  roleIds: [] as number[],
  postIds: [] as number[],
})
const assignForm = reactive({
  roleIds: [] as number[],
  postIds: [] as number[],
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<UserVo>(getUserPage, {
    buildParams: (query) => ({
      username: (query.username as string) || undefined,
      nickname: (query.nickname as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    avatar: '',
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: 1,
    deptId: undefined,
    roleIds: [],
    postIds: [],
  })
  modalOpen.value = true
}

function openEdit(record: UserVo) {
  editingId.value = record.id
  Object.assign(form, {
    avatar: record.avatar || '',
    username: record.username,
    password: '',
    nickname: record.nickname || '',
    email: record.email || '',
    phone: record.phone || '',
    status: record.status,
    deptId: record.deptId,
    roleIds: record.roleIds || [],
    postIds: record.postIds || [],
  })
  modalOpen.value = true
}

function openAssign(record: UserVo) {
  assignUserId.value = record.id
  assignForm.roleIds = record.roleIds || []
  assignForm.postIds = record.postIds || []
  assignOpen.value = true
}

async function onAssignSubmit() {
  if (!assignUserId.value) {
    return
  }
  assignSaving.value = true
  try {
    await assignUserRoles(assignUserId.value, assignForm.roleIds)
    await assignUserPosts(assignUserId.value, assignForm.postIds)
    message.success(t('page.userAssigned'))
    assignOpen.value = false
    loadData()
  } finally {
    assignSaving.value = false
  }
}

async function onSubmit() {
  if (!form.username || (!editingId.value && !form.password)) {
    message.warning(t('page.userUsernameRequired'))
    return
  }
  if (form.password && !isStrongPassword(form.password)) {
    message.warning(t('page.passwordPolicy'))
    return
  }
  saving.value = true
  try {
    const payload: UserSaveRequest = {
      avatar: form.avatar,
      username: form.username,
      password: form.password || undefined,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      status: form.status,
      deptId: form.deptId,
      roleIds: form.roleIds,
      postIds: form.postIds,
    }
    if (editingId.value) {
      payload.id = editingId.value
      await updateUser(payload)
    } else {
      await createUser(payload)
    }
    message.success(t('page.userSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onExport() {
  await exportUsers()
  message.success(t('page.userExportSuccess'))
}

async function onImport(file: File) {
  try {
    const count = await importUsers(file)
    message.success(t('page.userImportSuccess', { count }))
    loadData()
  } catch {
    message.error(t('page.userImportError'))
  }
  return false
}

async function toggleStatus(record: UserVo, checked: boolean) {
  await updateUserStatus(record.id, checked ? 1 : 0)
  message.success(t('page.userStatusUpdated'))
  loadData()
}

function onDelete(record: UserVo) {
  Modal.confirm({
    title: t('page.userDeleteTitle'),
    content: t('page.userDeleteConfirm', { name: record.username }),
    onOk: async () => {
      await deleteUser(record.id)
      message.success(t('page.userDeleted'))
      loadData()
    },
  })
}

onMounted(async () => {
  try {
    roleOptions.value = await getRoleOptions()
    postOptions.value = await getPostOptions()
    deptTree.value = await getDeptTree()
  } catch {
    // 选项/树加载失败保持空下拉（请求层已 toast），避免未捕获 rejection
    roleOptions.value = []
    postOptions.value = []
    deptTree.value = []
  }
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

