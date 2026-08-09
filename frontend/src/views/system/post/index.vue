<template>
  <a-card :title="t('page.postTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:post:add'" type="primary" @click="openCreate">{{ t('page.postAdd') }}</a-button>
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
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:post:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:post:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.postEdit') : t('page.postAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.postCode')" required>
          <a-input v-model:value="form.postCode" />
        </a-form-item>
        <a-form-item :label="t('page.postName')" required>
          <a-input v-model:value="form.postName" />
        </a-form-item>
        <a-form-item :label="t('page.postSort')">
          <a-input-number v-model:value="form.sort" />
        </a-form-item>
        <a-form-item :label="t('page.postDescription')">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item :label="t('page.postStatus')">
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
import { useTableQuery } from '@/composables/useTableQuery'
import { createPost, deletePost, getPostPage, updatePost } from '@/api/system'
import type { PostSaveRequest, PostVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.postCode'), prop: 'postCode', placeholder: `${t('common.inputPlaceholder')}${t('page.postCode')}` },
  { label: t('page.postName'), prop: 'postName', placeholder: `${t('common.inputPlaceholder')}${t('page.postName')}` },
  {
    label: t('page.postStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 },
    ],
  },
]

const columns = [
  { title: t('page.postCode'), dataIndex: 'postCode', key: 'postCode' },
  { title: t('page.postName'), dataIndex: 'postName', key: 'postName' },
  { title: t('page.postDescription'), dataIndex: 'description', key: 'description' },
  { title: t('page.postSort'), dataIndex: 'sort', key: 'sort', width: 80 },
  { title: t('page.postStatus'), key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive({
  postCode: '',
  postName: '',
  sort: 0,
  description: '',
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<PostVo>(getPostPage, {
    buildParams: (query) => ({
      postCode: (query.postCode as string) || undefined,
      postName: (query.postName as string) || undefined,
      status: query.status as number | undefined,
    }),
  })

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
    message.warning(t('page.postRequired'))
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
    message.success(t('page.postSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: PostVo) {
  Modal.confirm({
    title: t('page.postDeleteTitle'),
    content: t('page.postDeleteConfirm', { name: record.postName }),
    onOk: async () => {
      await deletePost(record.id)
      message.success(t('page.postDeleted'))
      loadData()
    },
  })
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

