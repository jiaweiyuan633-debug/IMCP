<template>
  <a-card :title="t('page.fileTitle')">
    <div v-if="quota" class="quota-bar">
      <span class="quota-label">{{ t('page.fileQuota') }}</span>
      <a-progress
        v-if="!quota.unlimited"
        :percent="quota.percent || 0"
        size="small"
        :status="quota.percent !== null && quota.percent >= 90 ? 'exception' : 'normal'"
        class="quota-progress"
      />
      <span v-else class="quota-unlimited">{{ t('page.fileQuotaUnlimited') }}</span>
    </div>
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
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
        <template v-if="column.key === 'originalName'">
          <a :href="withToken(record)" target="_blank" rel="noopener">{{ record.originalName || record.fileName }}</a>
        </template>
        <template v-else-if="column.key === 'size'">
          {{ formatSize(record.size) }}
        </template>
        <template v-else-if="column.key === 'storageType'">
          <a-tag :color="record.storageType === 'minio' ? 'purple' : 'green'">
            {{ record.storageType === 'minio' ? 'MinIO' : t('page.fileLocal') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'category'">
          <a-tag :color="categoryMeta[record.category || 'other']?.color || 'default'">
            {{ categoryMeta[record.category || 'other']?.label || record.category || t('page.fileCategoryOther') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'scanStatus'">
          <a-tag :color="scanStatusMeta(record.scanStatus).color">
            {{ scanStatusMeta(record.scanStatus).text }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a @click="onDownload(record)">{{ t('page.fileDownload') }}</a>
            <a @click="onCopyLink(record)">{{ t('page.fileCopyLink') }}</a>
            <a v-if="isImage(record)" @click="openPreview(record)">{{ t('common.preview') }}</a>
            <a v-else-if="isPdf(record)" @click="openPreview(record)">{{ t('common.preview') }}</a>
            <a v-permission="'system:file:delete'" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>
    <a-modal v-model:open="previewOpen" :title="previewName" :footer="null" width="720">
      <img v-if="isImage(previewRecord)" :src="previewUrl" alt="preview" class="preview-image" />
      <iframe v-else-if="isPdf(previewRecord)" :src="previewUrl" class="preview-pdf" />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import { deleteFile, downloadFile, getFilePage } from '@/api/system'
import { triggerBlobDownload } from '@/utils/download'
import { getFileAccessToken, getStorageQuota, type StorageQuota } from '@/api/common'
import type { FileVo } from '@/api/system'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { useTableQuery } from '@/composables/useTableQuery'

const { t } = useI18n()

const categoryMeta: Record<string, { color: string; label: string }> = {
  image: { color: 'green', label: t('page.fileCategoryImage') },
  pdf: { color: 'volcano', label: t('page.fileCategoryPdf') },
  office: { color: 'blue', label: t('page.fileCategoryOffice') },
  archive: { color: 'orange', label: t('page.fileCategoryArchive') },
  text: { color: 'cyan', label: t('page.fileCategoryText') },
  audio: { color: 'purple', label: t('page.fileCategoryAudio') },
  video: { color: 'magenta', label: t('page.fileCategoryVideo') },
  other: { color: 'default', label: t('page.fileCategoryOther') },
}

const searchFields: SearchField[] = [
  { label: t('page.fileStoredName'), prop: 'fileName', placeholder: `${t('common.inputPlaceholder')}${t('page.fileStoredName')}` },
  { label: t('page.fileOriginalName'), prop: 'originalName', placeholder: `${t('common.inputPlaceholder')}${t('page.fileOriginalName')}` },
  {
    label: t('page.fileCategory'),
    prop: 'category',
    type: 'select',
    options: Object.entries(categoryMeta).map(([value, meta]) => ({ label: meta.label, value })),
  },
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
  { title: t('page.fileCategory'), key: 'category', width: 90 },
  { title: t('page.fileStorageType'), key: 'storageType', width: 90 },
  { title: t('page.fileScanStatus'), key: 'scanStatus', width: 100 },
  { title: t('page.fileUploadTime'), dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: t('common.actions'), key: 'actions', width: 170 },
]

const previewOpen = ref(false)
const previewUrl = ref('')
const previewName = ref('')
const previewRecord = ref<FileVo | null>(null)
const quota = ref<StorageQuota | null>(null)

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<FileVo>(getFilePage, {
    buildParams: (query) => ({
      fileName: (query.fileName as string) || undefined,
      originalName: (query.originalName as string) || undefined,
      category: query.category as string | undefined,
      storageType: query.storageType as string | undefined,
    }),
  })

async function loadQuota() {
  try {
    quota.value = await getStorageQuota()
  } catch {
    quota.value = null
  }
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

function contentUrl(record: FileVo): string {
  return record.contentUrl || record.url
}

function isImage(record: FileVo | null): boolean {
  return !!record && (record.category === 'image' || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(record.url.split('?')[0]))
}

function isPdf(record: FileVo | null): boolean {
  return !!record && (record.category === 'pdf' || /\.pdf$/i.test(record.url.split('?')[0]))
}

function scanStatusMeta(status?: string): { text: string; color: string } {
  if (status === 'SCANNED') {
    return { text: t('page.fileScanPassed'), color: 'green' }
  }
  if (status === 'SCANNED_WARN') {
    return { text: t('page.fileScanWarn'), color: 'orange' }
  }
  return { text: t('page.fileScanSkipped'), color: 'default' }
}

async function openPreview(record: FileVo) {
  previewRecord.value = record
  previewUrl.value = await freshUrl(record)
  previewName.value = record.originalName || record.fileName
  previewOpen.value = true
}

function withToken(record: FileVo): string {
  const url = contentUrl(record)
  if (!record.accessToken) {
    return url
  }
  return `${url}?token=${encodeURIComponent(record.accessToken)}`
}

async function onCopyLink(record: FileVo) {
  await navigator.clipboard.writeText(await freshUrl(record))
  message.success(t('page.fileCopied'))
}

async function freshUrl(record: FileVo): Promise<string> {
  const url = contentUrl(record)
  const token = await getFileAccessToken(url)
  return `${url}?token=${encodeURIComponent(token)}`
}

async function onDownload(record: FileVo) {
  try {
    const blob = await downloadFile(record.id)
    triggerBlobDownload(blob, record.originalName || record.fileName)
  } catch {
    message.error(t('page.fileDownloadFailed'))
  }
}

function onDelete(record: FileVo) {
  Modal.confirm({
    title: t('page.fileDeleteTitle'),
    content: t('page.fileDeleteConfirm', { name: record.originalName || record.fileName }),
    onOk: async () => {
      await deleteFile(record.id)
      message.success(t('page.fileDeleted'))
      loadData()
      loadQuota()
    },
  })
}

loadQuota()
</script>

<style scoped>
.quota-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.quota-label {
  flex: none;
  color: var(--color-text-secondary, rgba(0, 0, 0, 0.65));
  font-size: 14px;
}

.quota-progress {
  flex: 1;
  max-width: 320px;
}

.quota-unlimited {
  color: var(--color-text-secondary, rgba(0, 0, 0, 0.65));
  font-size: 14px;
}

.preview-image {
  width: 100%;
  max-height: 560px;
  object-fit: contain;
}

.preview-pdf {
  width: 100%;
  height: 560px;
  border: 0;
}
</style>
