<template>
  <a-card :title="t('page.roleTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:role:add'" type="primary" @click="openCreate">{{ t('page.roleAdd') }}</a-button>
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
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'dataScope'">
          {{ dataScopeText(record.dataScope) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:role:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:role:assign'" @click="openAssign(record)">{{ t('page.roleAssignMenu') }}</a>
            <a v-permission="'system:role:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.roleEdit') : t('page.roleAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.roleCode')" required>
          <a-input v-model:value="form.code" />
        </a-form-item>
        <a-form-item :label="t('page.roleName')" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item :label="t('page.roleDescription')">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item :label="t('page.roleSort')">
          <a-input-number v-model:value="form.sort" />
        </a-form-item>
        <a-form-item :label="t('page.roleStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.roleDataScope')">
          <a-select v-model:value="form.dataScope" :options="dataScopeOptions" />
        </a-form-item>
        <a-form-item v-if="form.dataScope === 2" :label="t('page.roleAssignDept')">
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
      :title="t('page.roleAssignMenuTitle')"
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
import { useTableQuery } from '@/composables/useTableQuery'
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.roleCode'), prop: 'code', placeholder: `${t('common.inputPlaceholder')}${t('page.roleCode')}` },
  { label: t('page.roleName'), prop: 'name', placeholder: `${t('common.inputPlaceholder')}${t('page.roleName')}` },
  {
    label: t('page.roleStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.roleCode'), dataIndex: 'code', key: 'code' },
  { title: t('page.roleName'), dataIndex: 'name', key: 'name' },
  { title: t('page.roleDescription'), dataIndex: 'description', key: 'description' },
  { title: t('page.roleStatus'), key: 'status', width: 90 },
  { title: t('page.roleDataScope'), key: 'dataScope', width: 110 },
  { title: t('page.roleSort'), dataIndex: 'sort', key: 'sort', width: 80 },
  { title: t('common.actions'), key: 'actions', width: 180 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const dataScopeOptions = [
  { label: t('page.scopeAll'), value: 1 },
  { label: t('page.scopeCustom'), value: 2 },
  { label: t('page.scopeDept'), value: 3 },
  { label: t('page.scopeDeptChild'), value: 4 },
  { label: t('page.scopeSelf'), value: 5 },
]

const saving = ref(false)
const modalOpen = ref(false)
const assignOpen = ref(false)
const editingId = ref<number | undefined>()
const currentAssignRole = ref<RoleVo | null>(null)
const checkedMenuKeys = ref<number[]>([])
const menuTreeData = ref<MenuNode[]>([])
const deptTreeData = ref<DeptVo[]>([])
const checkedDeptKeys = ref<number[]>([])
const form = reactive({
  code: '',
  name: '',
  description: '',
  status: 1,
  dataScope: 1,
  sort: 0,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<RoleVo>(getRolePage, {
    buildParams: (query) => ({
      code: (query.code as string) || undefined,
      name: (query.name as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

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
    message.warning(t('page.roleRequired'))
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
    message.success(t('page.roleSaved'))
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
      message.success(t('page.roleAssignSuccess'))
      assignOpen.value = false
      loadData()
    })
    .finally(() => {
      saving.value = false
    })
}

function onDelete(record: RoleVo) {
  Modal.confirm({
    title: t('page.roleDeleteTitle'),
    content: t('page.roleDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteRole(record.id)
      message.success(t('page.roleDeleted'))
      loadData()
    },
  })
}

onMounted(async () => {
  try {
    menuTreeData.value = await getMenuTree()
    deptTreeData.value = await getDeptTree()
  } catch {
    // 菜单/部门树加载失败保持空树（请求层已 toast），避免未捕获 rejection
    menuTreeData.value = []
    deptTreeData.value = []
  }
})

function dataScopeText(scope: number) {
  const map: Record<number, string> = {
    1: t('page.scopeAll'),
    2: t('page.scopeCustom'),
    3: t('page.scopeDept'),
    4: t('page.scopeDeptChild'),
    5: t('page.scopeSelf'),
  }
  return map[scope] || String(scope)
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

