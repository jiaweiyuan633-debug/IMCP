<template>
  <a-card title="菜单管理">
    <div class="toolbar">
      <a-button v-permission="'system:menu:add'" type="primary" @click="openCreate()">新增菜单</a-button>
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
            <a v-permission="'system:menu:add'" @click="openCreate(record)">新增子级</a>
            <a v-permission="'system:menu:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:menu:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑菜单' : '新增菜单'"
      :loading="saving"
      width="520"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="上级菜单">
          <a-tree-select
            v-model:value="form.parentId"
            :tree-data="parentTreeData"
            tree-default-expand-all
            :field-names="{ label: 'name', value: 'id', children: 'children' }"
          />
        </a-form-item>
        <a-form-item label="菜单名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="form.type" :options="typeOptions" />
        </a-form-item>
        <a-form-item v-if="form.type !== 'button'" label="路由路径">
          <a-input v-model:value="form.path" />
        </a-form-item>
        <a-form-item v-if="form.type === 'menu'" label="组件路径">
          <a-input v-model:value="form.component" placeholder="例如 system/user" />
        </a-form-item>
        <a-form-item label="权限标识">
          <a-input v-model:value="form.perm" placeholder="例如 system:user:add" />
        </a-form-item>
        <a-form-item label="图标">
          <a-input v-model:value="form.icon" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sort" />
        </a-form-item>
        <a-form-item label="显示">
          <a-select v-model:value="form.visible" :options="showOptions" />
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
import { createMenu, deleteMenu, getMenuTree, updateMenu } from '@/api/system'
import type { MenuSaveRequest } from '@/api/system'
import type { MenuNode } from '@/types'

const columns = [
  { title: '菜单名称', dataIndex: 'name', key: 'name' },
  { title: '类型', key: 'type', width: 90 },
  { title: '路由/组件', key: 'route', customRender: ({ record }: { record: MenuNode }) =>
      record.component || record.path || '-' },
  { title: '权限标识', dataIndex: 'perm', key: 'perm' },
  { title: '图标', dataIndex: 'icon', key: 'icon' },
  { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 180 },
]

const typeOptions = [
  { label: '目录', value: 'dir' },
  { label: '菜单', value: 'menu' },
  { label: '按钮', value: 'button' },
]

const showOptions = [
  { label: '显示', value: 1 },
  { label: '隐藏', value: 0 },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
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
  { id: 0, parentId: 0, name: '根菜单', type: 'dir', sort: 0, visible: 1, status: 1, children: menuTreeData.value },
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
  const map: Record<string, string> = { dir: '目录', menu: '菜单', button: '按钮' }
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
    message.warning('请填写菜单名称')
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
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: MenuNode) {
  Modal.confirm({
    title: '确认删除菜单',
    content: `确定删除菜单 ${record.name} 吗？子菜单会一并删除。`,
    onOk: async () => {
      await deleteMenu(record.id)
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

