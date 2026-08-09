<template>
  <a-card :title="t('page.fileTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
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
        <template v-if="column.key === 'originalName'">
          <a :href="record.url" target="_blank" rel="noopener">{{ record.originalName || record.fileName }}</a>
        </template>
        <template v-else-if="column.key === 'size'">
          {{ formatSize(record.size) }}
        </template>
        <template v-else-if="column.key === 'storageType'">
          <a-tag :color="record.storageType === 'minio' ? 'purple' : 'green'">
            {{ record.storageType === 'minio' ? 'MinIO' : t('page.fileLocal') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a v-permission="'system:file:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
        </template>
      </template>
    </ProTable>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import { deleteFile, getFilePage } from '@/api/system'
import type { FileVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.fileStoredName'), prop: 'fileName', placeholder: `${t('common.inputPlaceholder')}${t('page.fileStoredName')}` },
  { label: t('page.fileOriginalName'), prop: 'originalName', placeholder: `${t('common.inputPlaceholder')}${t('page.fileOriginalName')}` },
  {
    label: t('page.fileStorageType'),
    prop: 'storageType',
    type: 'select',
    options: [
      { label: t('page.fileLocal'), value: 'local' },
      { label: 'MinIO', value: 'minio' },
    ],
  },
]

const columns = [
  { title: t('page.fileOriginalName'), key: 'originalName', ellipsis: true },
  { title: t('page.fileStoredName'), dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: t('page.fileSize'), key: 'size', width: 100 },
  { title: t('page.fileStorageType'), key: 'storageType', width: 100 },
  { title: t('page.fileUploadTime'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('common.actions'), key: 'actions', width: 90 },
]

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const records = ref<FileVo[]>([])
const searchModel = reactive<Record<string, unknown>>({})

async function loadData() {
  loading.value = true
  try {
    const data = await getFilePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      fileName: (searchModel.fileName as string) || undefined,
      originalName: (searchModel.originalName as string) || undefined,
      storageType: searchModel.storageType as string | undefined,
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

function formatSize(size: number): string {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function onDelete(record: FileVo) {
  Modal.confirm({
    title: t('page.fileDeleteTitle'),
    content: t('page.fileDeleteConfirm', { name: record.originalName || record.fileName }),
    onOk: async () => {
      await deleteFile(record.id)
      message.success(t('page.fileDeleted'))
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
