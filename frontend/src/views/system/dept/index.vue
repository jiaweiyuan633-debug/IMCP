<template>
  <a-card :title="t('page.deptTitle')">
    <div class="toolbar">
      <a-button v-permission="'system:dept:add'" type="primary" @click="openCreate()">{{ t('page.deptAdd') }}</a-button>
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
            <a v-permission="'system:dept:add'" @click="openCreate(record)">{{ t('page.deptAddChild') }}</a>
            <a v-permission="'system:dept:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:dept:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.deptEdit') : t('page.deptAdd')"
      :loading="saving"
      width="520"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.deptParent')">
          <a-tree-select
            v-model:value="form.parentId"
            :tree-data="parentTreeData"
            tree-default-expand-all
            :field-names="{ label: 'deptName', value: 'id', children: 'children' }"
          />
        </a-form-item>
        <a-form-item :label="t('page.deptName')" required>
          <a-input v-model:value="form.deptName" />
        </a-form-item>
        <a-form-item :label="t('page.deptLeader')">
          <a-input v-model:value="form.leader" />
        </a-form-item>
        <a-form-item :label="t('page.deptPhone')">
          <a-input v-model:value="form.phone" />
        </a-form-item>
        <a-form-item :label="t('page.deptEmail')">
          <a-input v-model:value="form.email" />
        </a-form-item>
        <a-form-item :label="t('page.deptSort')">
          <a-input-number v-model:value="form.orderNum" />
        </a-form-item>
        <a-form-item :label="t('page.deptStatus')">
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const columns = [
  { title: t('page.deptName'), dataIndex: 'deptName', key: 'deptName' },
  { title: t('page.deptLeader'), dataIndex: 'leader', key: 'leader' },
  { title: t('page.deptPhone'), dataIndex: 'phone', key: 'phone' },
  { title: t('page.deptEmail'), dataIndex: 'email', key: 'email' },
  { title: t('page.deptSort'), dataIndex: 'orderNum', key: 'orderNum', width: 80 },
  { title: t('page.deptStatus'), key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 180 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
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
  { id: 0, parentId: 0, deptName: t('page.deptRoot'), orderNum: 0, status: 1, children: tree.value },
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
    message.warning(t('page.deptRequired'))
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
    message.success(t('page.deptSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: DeptVo) {
  Modal.confirm({
    title: t('page.deptDeleteTitle'),
    content: t('page.deptDeleteConfirm', { name: record.deptName }),
    onOk: async () => {
      await deleteDept(record.id)
      message.success(t('page.deptDeleted'))
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

