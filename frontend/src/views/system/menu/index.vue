<template>
  <a-card :title="t('page.menuTitle')">
    <div class="toolbar">
      <a-button v-permission="'system:menu:add'" type="primary" @click="openCreate()">{{ t('page.menuAdd') }}</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="menuTreeData"
      :loading="loading"
      row-key="id"
      children-column-name="children"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          <a-tag>{{ typeText(record.type) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:menu:add'" @click="openCreate(record)">{{ t('page.menuAddChild') }}</a>
            <a v-permission="'system:menu:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:menu:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.menuEdit') : t('page.menuAdd')"
      :loading="saving"
      width="520"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.menuParent')">
          <a-tree-select
            v-model:value="form.parentId"
            :tree-data="parentTreeData"
            tree-default-expand-all
            :field-names="{ label: 'name', value: 'id', children: 'children' }"
          />
        </a-form-item>
        <a-form-item :label="t('page.menuName')" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item :label="t('page.menuType')">
          <a-select v-model:value="form.type" :options="typeOptions" />
        </a-form-item>
        <a-form-item v-if="form.type !== 'button'" :label="t('page.menuRoute')">
          <a-input v-model:value="form.path" />
        </a-form-item>
        <a-form-item v-if="form.type === 'menu'" :label="t('page.menuComponent')">
          <a-input v-model:value="form.component" placeholder="e.g. system/user" />
        </a-form-item>
        <a-form-item :label="t('page.menuPerm')">
          <a-input v-model:value="form.perm" placeholder="e.g. system:user:add" />
        </a-form-item>
        <a-form-item :label="t('page.menuIcon')">
          <a-input v-model:value="form.icon" />
        </a-form-item>
        <a-form-item :label="t('page.menuSort')">
          <a-input-number v-model:value="form.sort" />
        </a-form-item>
        <a-form-item :label="t('page.menuVisible')">
          <a-select v-model:value="form.visible" :options="showOptions" />
        </a-form-item>
        <a-form-item :label="t('page.menuStatus')">
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
import { createMenu, deleteMenu, getMenuTree, updateMenu } from '@/api/system'
import type { MenuSaveRequest } from '@/api/system'
import type { MenuNode } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const columns = [
  { title: t('page.menuName'), dataIndex: 'name', key: 'name' },
  { title: t('page.menuType'), key: 'type', width: 90 },
  { title: `${t('page.menuRoute')}/${t('page.menuComponent')}`, key: 'route', customRender: ({ record }: { record: MenuNode }) =>
      record.component || record.path || '-' },
  { title: t('page.menuPerm'), dataIndex: 'perm', key: 'perm' },
  { title: t('page.menuIcon'), dataIndex: 'icon', key: 'icon' },
  { title: t('page.menuSort'), dataIndex: 'sort', key: 'sort', width: 80 },
  { title: t('page.menuStatus'), key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 180 },
]

const typeOptions = [
  { label: t('page.menuTypeDir'), value: 'dir' },
  { label: t('page.menuTypeMenu'), value: 'menu' },
  { label: t('page.menuTypeButton'), value: 'button' },
]

const showOptions = [
  { label: t('page.menuShow'), value: 1 },
  { label: t('page.menuHide'), value: 0 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const menuTreeData = ref<MenuNode[]>([])
const form = reactive({
  parentId: 0,
  name: '',
  type: 'menu' as 'dir' | 'menu' | 'button',
  path: '',
  component: '',
  perm: '',
  icon: '',
  sort: 0,
  visible: 1,
  status: 1,
})

const parentTreeData = computed<MenuNode[]>(() => [
  { id: 0, parentId: 0, name: t('page.menuRoot'), type: 'dir', sort: 0, visible: 1, status: 1, children: menuTreeData.value },
])

async function loadData() {
  loading.value = true
  try {
    menuTreeData.value = await getMenuTree()
  } finally {
    loading.value = false
  }
}

function typeText(type: string) {
  const map: Record<string, string> = { dir: t('page.menuTypeDir'), menu: t('page.menuTypeMenu'), button: t('page.menuTypeButton') }
  return map[type] || type
}

function openCreate(parent?: MenuNode) {
  editingId.value = undefined
  Object.assign(form, {
    parentId: parent ? parent.id : 0,
    name: '',
    type: parent?.type === 'dir' ? 'menu' : 'dir',
    path: '',
    component: '',
    perm: '',
    icon: '',
    sort: 0,
    visible: 1,
    status: 1,
  })
  modalOpen.value = true
}

function openEdit(record: MenuNode) {
  editingId.value = record.id
  Object.assign(form, {
    parentId: record.parentId,
    name: record.name,
    type: record.type,
    path: record.path || '',
    component: record.component || '',
    perm: record.perm || '',
    icon: record.icon || '',
    sort: record.sort,
    visible: record.visible,
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.name) {
    message.warning(t('page.menuRequired'))
    return
  }
  saving.value = true
  try {
    const payload: MenuSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateMenu(payload)
    } else {
      await createMenu(payload)
    }
    message.success(t('page.menuSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: MenuNode) {
  Modal.confirm({
    title: t('page.menuDeleteTitle'),
    content: t('page.menuDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteMenu(record.id)
      message.success(t('page.menuDeleted'))
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

