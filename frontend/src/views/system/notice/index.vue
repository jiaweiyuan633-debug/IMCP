<template>
  <a-card :title="t('page.noticeTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:notice:add'" type="primary" @click="openCreate">{{ t('page.noticeAdd') }}</a-button>
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
        <template v-if="column.key === 'noticeType'">
          <a-tag :color="record.noticeType === 1 ? 'blue' : 'green'">
            {{ record.noticeType === 1 ? t('page.noticeNotice') : t('page.noticeAnnounce') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:notice:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:notice:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.noticeEdit') : t('page.noticeAdd')"
      :loading="saving"
      width="560"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.noticeTitleField')" required>
          <a-input v-model:value="form.noticeTitle" />
        </a-form-item>
        <a-form-item :label="t('page.noticeType')">
          <a-select v-model:value="form.noticeType" :options="typeOptions" />
        </a-form-item>
        <a-form-item :label="t('page.noticeContent')">
          <a-textarea v-model:value="form.noticeContent" :rows="5" />
        </a-form-item>
        <a-form-item :label="t('page.noticeStatus')">
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
import { createNotice, deleteNotice, getNoticePage, updateNotice } from '@/api/system'
import type { NoticeVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.noticeTitleField'), prop: 'title', placeholder: `${t('common.inputPlaceholder')}${t('page.noticeTitleField')}` },
  {
    label: t('page.noticeType'),
    prop: 'type',
    type: 'select',
    options: [
      { label: t('page.noticeNotice'), value: 1 },
      { label: t('page.noticeAnnounce'), value: 2 },
    ],
  },
]

const columns = [
  { title: t('page.noticeTitleField'), dataIndex: 'noticeTitle', key: 'noticeTitle' },
  { title: t('page.noticeType'), key: 'noticeType', width: 90 },
  { title: t('page.noticeStatus'), key: 'status', width: 90 },
  { title: t('common.createdAt'), dataIndex: 'createdAt', key: 'createdAt' },
  { title: t('common.actions'), key: 'actions', width: 130 },
]

const typeOptions = [
  { label: t('page.noticeNotice'), value: 1 },
  { label: t('page.noticeAnnounce'), value: 2 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const records = ref<NoticeVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})
const form = reactive({
  noticeTitle: '',
  noticeType: 1,
  noticeContent: '',
  status: 1,
})

async function loadData() {
  loading.value = true
  try {
    const data = await getNoticePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      title: (searchModel.title as string) || undefined,
      type: searchModel.type as number | undefined,
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
  Object.assign(form, { noticeTitle: '', noticeType: 1, noticeContent: '', status: 1 })
  modalOpen.value = true
}

function openEdit(record: NoticeVo) {
  editingId.value = record.id
  Object.assign(form, {
    noticeTitle: record.noticeTitle,
    noticeType: record.noticeType,
    noticeContent: record.noticeContent || '',
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.noticeTitle) {
    message.warning(t('page.noticeTitleRequired'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateNotice({ ...form, id: editingId.value })
    } else {
      await createNotice(form)
    }
    message.success(t('page.noticeSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: NoticeVo) {
  Modal.confirm({
    title: t('page.noticeDeleteTitle'),
    content: t('page.noticeDeleteConfirm', { name: record.noticeTitle }),
    onOk: async () => {
      await deleteNotice(record.id)
      message.success(t('page.noticeDeleted'))
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

