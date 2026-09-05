<template>
  <a-card :title="t('page.ieJobTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'importexport:job:create'" @click="openImport">
        <DownloadOutlined :rotate="180" />
        {{ t('page.ieJobCreateImport') }}
      </a-button>
      <a-button v-permission="'importexport:job:create'" type="primary" @click="openExport">
        <DownloadOutlined />
        {{ t('page.ieJobCreateExport') }}
      </a-button>
      <a-button @click="loadData">
        <ReloadOutlined />
        {{ t('common.refresh') }}
      </a-button>
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
        <template v-if="column.key === 'type'">
          <a-tag :color="record.type === 'import' ? 'green' : 'blue'">
            {{ record.type === 'import' ? t('page.ieTemplateTypeImport') : t('page.ieTemplateTypeExport') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusMeta(record.status).color">{{ statusMeta(record.status).text }}</a-tag>
        </template>
        <template v-else-if="column.key === 'progress'">
          <span v-if="record.total">
            {{ record.success }}/{{ record.total }}
            <span v-if="record.failed" class="danger">（{{ record.failed }} {{ t('page.ieJobFailed') }}）</span>
          </span>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.key === 'errorMessage'">
          <a-tooltip v-if="record.errorMessage" :title="record.errorMessage">
            <span class="danger">{{ record.errorMessage }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a
              v-if="record.type === 'export' && record.status === 'SUCCEEDED'"
              v-permission="'importexport:job:download'"
              @click="onDownload(record)"
            >
              {{ t('page.ieJobDownload') }}
            </a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <a-modal v-model:open="importOpen" :title="t('page.ieJobCreateImport')" :confirm-loading="creating" @ok="onCreateImport">
      <a-form layout="vertical">
        <a-form-item :label="t('page.ieJobTemplate')" required>
          <a-select v-model:value="importForm.templateCode" :options="importTemplates" show-search option-filter-prop="label" />
        </a-form-item>
        <a-form-item :label="t('page.ieJobBizNo')" required>
          <a-input v-model:value="importForm.bizNo" :maxlength="64" :placeholder="t('page.ieJobBizNoPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.ieJobFile')" required>
          <a-upload :before-upload="handleImportFile" :show-upload-list="false" accept=".xlsx,.xls,.csv">
            <a-button :loading="uploading">
              <UploadOutlined />
              {{ importForm.fileName || t('page.ieJobSelectFile') }}
            </a-button>
          </a-upload>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="exportOpen" :title="t('page.ieJobCreateExport')" :confirm-loading="creating" @ok="onCreateExport">
      <a-form layout="vertical">
        <a-form-item :label="t('page.ieJobTemplate')" required>
          <a-select v-model:value="exportForm.templateCode" :options="exportTemplates" show-search option-filter-prop="label" />
        </a-form-item>
        <a-form-item :label="t('page.ieJobBizNo')" required>
          <a-input v-model:value="exportForm.bizNo" :maxlength="64" :placeholder="t('page.ieJobBizNoPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('page.ieJobQuery')">
          <a-textarea v-model:value="exportForm.queryJson" :rows="4" :placeholder="t('page.ieJobQueryPlaceholder')" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { uploadFile } from '@/api/common'
import { absoluteFileUrl } from '@/utils/fileUrl'
import {
  createExportJob,
  createImportJob,
  getImportJobDownload,
  getImportJobPage,
  getImportTemplatePage,
} from '@/api/importExport'
import type { ImportExportJobVo, ImportExportTemplateVo } from '@/api/importExport'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'
import { dateColumn } from '@/utils/table'

const { t } = useI18n()

const searchFields: SearchField[] = [
  { label: t('page.ieJobTemplateCode'), prop: 'templateCode', placeholder: `${t('common.inputPlaceholder')}${t('page.ieJobTemplateCode')}` },
  {
    label: t('page.ieTemplateType'),
    prop: 'type',
    type: 'select',
    options: [
      { label: t('page.ieTemplateTypeImport'), value: 'import' },
      { label: t('page.ieTemplateTypeExport'), value: 'export' },
    ],
  },
  {
    label: t('page.ieJobStatus'),
    prop: 'status',
    type: 'select',
    options: [
      { label: t('page.ieJobStatusPending'), value: 'PENDING' },
      { label: t('page.ieJobStatusSucceeded'), value: 'SUCCEEDED' },
      { label: t('page.ieJobStatusFailed'), value: 'FAILED' },
    ],
  },
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: t('page.ieJobTemplateCode'), dataIndex: 'templateCode', key: 'templateCode', width: 140 },
  { title: t('page.ieTemplateType'), dataIndex: 'type', key: 'type', width: 90 },
  { title: t('page.ieJobStatus'), dataIndex: 'status', key: 'status', width: 100 },
  { title: t('page.ieJobFile'), dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: t('page.ieJobProgress'), dataIndex: 'total', key: 'progress', width: 130 },
  { title: t('page.ieJobError'), dataIndex: 'errorMessage', key: 'errorMessage', ellipsis: true },
  dateColumn('createdAt', { title: t('page.ieJobCreatedAt'), width: 170 }),
  { title: t('common.actions'), key: 'actions', width: 100 },
]

function statusMeta(status?: string): { text: string; color: string } {
  if (status === 'SUCCEEDED') {
    return { text: t('page.ieJobStatusSucceeded'), color: 'green' }
  }
  if (status === 'FAILED') {
    return { text: t('page.ieJobStatusFailed'), color: 'red' }
  }
  return { text: t('page.ieJobStatusPending'), color: 'blue' }
}

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<ImportExportJobVo>(getImportJobPage, {
    buildParams: (query) => ({
      templateCode: (query.templateCode as string) || undefined,
      type: (query.type as string) || undefined,
      status: (query.status as string) || undefined,
    }),
  })

// ==================== 模板选项 ====================

const allTemplates = ref<ImportExportTemplateVo[]>([])

async function loadTemplates() {
  try {
    const page = await getImportTemplatePage({ pageNum: 1, pageSize: 1000 })
    allTemplates.value = page.records
  } catch {
    allTemplates.value = []
  }
}

function toOptions(templates: ImportExportTemplateVo[]) {
  return templates.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.code }))
}

const importTemplates = ref<{ label: string; value: string }[]>([])
const exportTemplates = ref<{ label: string; value: string }[]>([])
loadTemplates()

// ==================== 导入任务 ====================

const importOpen = ref(false)
const creating = ref(false)
const uploading = ref(false)
const importForm = reactive<{ templateCode?: string; bizNo: string; fileId?: number; fileName: string }>({
  templateCode: undefined,
  bizNo: '',
  fileId: undefined,
  fileName: '',
})

function openImport() {
  importForm.templateCode = undefined
  importForm.bizNo = ''
  importForm.fileId = undefined
  importForm.fileName = ''
  importTemplates.value = toOptions(allTemplates.value.filter((item) => item.type === 'import' && item.status === 1))
  importOpen.value = true
}

async function handleImportFile(file: File): Promise<boolean> {
  uploading.value = true
  try {
    const result = await uploadFile(file)
    importForm.fileId = result.id
    importForm.fileName = result.name
    message.success(t('page.ieJobFileUploaded'))
  } catch {
    message.error(t('page.ieJobFileUploadFailed'))
  } finally {
    uploading.value = false
  }
  return false
}

async function onCreateImport() {
  if (!importForm.templateCode || !importForm.bizNo || !importForm.fileId) {
    message.warning(t('page.ieJobCreateRequired'))
    return
  }
  creating.value = true
  try {
    await createImportJob({
      bizNo: importForm.bizNo,
      templateCode: importForm.templateCode,
      fileId: importForm.fileId,
      fileName: importForm.fileName,
    })
    message.success(t('page.ieJobCreated'))
    importOpen.value = false
    loadData()
  } finally {
    creating.value = false
  }
}

// ==================== 导出任务 ====================

const exportOpen = ref(false)
const exportForm = reactive<{ templateCode?: string; bizNo: string; queryJson: string }>({
  templateCode: undefined,
  bizNo: '',
  queryJson: '',
})

function openExport() {
  exportForm.templateCode = undefined
  exportForm.bizNo = ''
  exportForm.queryJson = ''
  exportTemplates.value = toOptions(allTemplates.value.filter((item) => item.type === 'export' && item.status === 1))
  exportOpen.value = true
}

async function onCreateExport() {
  if (!exportForm.templateCode || !exportForm.bizNo) {
    message.warning(t('page.ieJobCreateRequired'))
    return
  }
  let query: Record<string, unknown> | undefined
  if (exportForm.queryJson.trim()) {
    try {
      query = JSON.parse(exportForm.queryJson)
    } catch {
      message.error(t('page.ieJobQueryInvalid'))
      return
    }
  }
  creating.value = true
  try {
    await createExportJob({
      bizNo: exportForm.bizNo,
      templateCode: exportForm.templateCode,
      query,
    })
    message.success(t('page.ieJobCreated'))
    exportOpen.value = false
    loadData()
  } finally {
    creating.value = false
  }
}

// ==================== 下载结果 ====================

async function onDownload(record: ImportExportJobVo) {
  try {
    const result = await getImportJobDownload(record.id)
    // origin 拼接统一走 absoluteFileUrl（基于 API_BASE_URL，已去尾部 /）
    window.open(absoluteFileUrl(result.url), '_blank')
  } catch {
    message.error(t('page.ieJobDownloadFailed'))
  }
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.danger {
  color: #ff4d4f;
}
</style>
