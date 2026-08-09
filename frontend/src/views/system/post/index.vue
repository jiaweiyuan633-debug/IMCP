<template>
  <a-card title="岗位管理">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:post:add'" type="primary" @click="openCreate">新增岗位</a-button>
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
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:post:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'system:post:delete'" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑岗位' : '新增岗位'"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="岗位编码" required>
          <a-input v-model:value="form.postCode" />
        </a-form-item>
        <a-form-item label="岗位名称" required>
          <a-input v-model:value="form.postName" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sort" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { createPost, deletePost, getPostPage, updatePost } from '@/api/system'
import type { PostSaveRequest, PostVo } from '@/api/system'
import type { SearchField } from '@/types'

const searchFields: SearchField[] = [
  { label: '岗位编码', prop: 'postCode', placeholder: '请输入岗位编码' },
  { label: '岗位名称', prop: 'postName', placeholder: '请输入岗位名称' },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '停用', value: 0 },
    ],
  },
]

const columns = [
  { title: '岗位编码', dataIndex: 'postCode', key: 'postCode' },
  { title: '岗位名称', dataIndex: 'postName', key: 'postName' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 130 },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const records = ref<PostVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({
  postCode: '',
  postName: '',
  sort: 0,
  description: '',
  status: 1,
})

async function loadData() {
  loading.value = true
  try {
    const data = await getPostPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      postCode: (searchModel.postCode as string) || undefined,
      postName: (searchModel.postName as string) || undefined,
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
  Object.assign(form, { postCode: '', postName: '', sort: 0, description: '', status: 1 })
  modalOpen.value = true
}

function openEdit(record: PostVo) {
  editingId.value = record.id
  Object.assign(form, {
    postCode: record.postCode,
    postName: record.postName,
    sort: record.sort,
    description: record.description || '',
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.postCode || !form.postName) {
    message.warning('请填写岗位编码和名称')
    return
  }
  saving.value = true
  try {
    const payload: PostSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updatePost(payload)
    } else {
      await createPost(payload)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: PostVo) {
  Modal.confirm({
    title: '确认删除岗位',
    content: `确定删除岗位 ${record.postName} 吗？`,
    onOk: async () => {
      await deletePost(record.id)
      message.success('删除成功')
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

