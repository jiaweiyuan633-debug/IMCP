<template>
  <a-card :title="t('page.aiKnowledgeTitle')">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'ai:knowledge:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.aiKnowledgeAdd') }}
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
        <template v-if="column.key === 'status'">
          <StatusTag :value="record.status" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'ai:knowledge:list'" @click="openDocs(record)">{{ t('page.aiKnowledgeDocManage') }}</a>
            <a v-permission="'ai:knowledge:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'ai:knowledge:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.aiKnowledgeEdit') : t('page.aiKnowledgeAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.aiKnowledgeName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.aiKnowledgeDescription')">
          <a-textarea v-model:value="form.description" :rows="3" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.aiKnowledgeStatus')">
          <a-select v-model:value="form.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-drawer
      :open="docsDrawerOpen"
      :title="`${t('page.aiKnowledgeDocTitle')}：${currentBaseName}`"
      width="720"
      @close="closeDocs"
    >
      <div class="toolbar">
        <a-button v-permission="'ai:knowledge:doc:add'" type="primary" @click="openDocCreate">
          <PlusOutlined />
          {{ t('page.aiKnowledgeDocAdd') }}
        </a-button>
      </div>
      <ProTable
        v-model:page-num="docPageNum"
        v-model:page-size="docPageSize"
        :columns="docColumns"
        :data-source="docRecords"
        :loading="docLoading"
        :total="docTotal"
        :error="docError"
        row-key="id"
        @change="docLoadData"
        @retry="docLoadData"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag :value="record.status" />
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a v-permission="'ai:knowledge:edit'" @click="openDocEdit(record)">{{ t('common.edit') }}</a>
              <a v-permission="'ai:knowledge:doc:delete'" class="danger" @click="onDocDelete(record)">{{ t('common.delete') }}</a>
            </a-space>
          </template>
        </template>
      </ProTable>

      <ModalForm
        v-model:open="docModalOpen"
        :title="docEditingId ? t('page.aiKnowledgeDocEdit') : t('page.aiKnowledgeDocAdd')"
        :loading="docSaving"
        @ok="onDocSubmit"
      >
        <a-form layout="vertical" :model="docForm">
          <a-form-item :label="t('page.aiKnowledgeDocName')" required>
            <a-input v-model:value="docForm.title" :maxlength="200" />
          </a-form-item>
          <a-form-item :label="t('page.aiKnowledgeDocContent')">
            <a-textarea v-model:value="docForm.content" :rows="8" />
          </a-form-item>
          <a-form-item :label="t('page.aiKnowledgeStatus')">
            <a-select v-model:value="docForm.status" :options="statusOptions" />
          </a-form-item>
        </a-form>
      </ModalForm>
    </a-drawer>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import {
  createKnowledge,
  createKnowledgeDoc,
  deleteKnowledge,
  deleteKnowledgeDoc,
  getKnowledgeDocPage,
  getKnowledgePage,
  updateKnowledge,
  updateKnowledgeDoc,
} from '@/api/aiEnhance'
import type { KnowledgeBaseSaveRequest, KnowledgeBaseVo, KnowledgeDocSaveRequest, KnowledgeDocVo } from '@/api/aiEnhance'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const searchFields: SearchField[] = [
  {
    label: t('page.aiKnowledgeName'),
    prop: 'name',
    placeholder: `${t('common.inputPlaceholder')}${t('page.aiKnowledgeName')}`,
  },
]

const columns = [
  { title: t('page.aiKnowledgeName'), dataIndex: 'name', key: 'name' },
  { title: t('page.aiKnowledgeDescription'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('page.aiKnowledgeStatus'), dataIndex: 'status', key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 180 },
]

const statusOptions = [
  { label: t('common.enabled'), value: 1 },
  { label: t('common.disabled'), value: 0 },
]

// ---------- 知识库 ----------

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<KnowledgeBaseSaveRequest>({
  name: '',
  description: '',
  status: 1,
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<KnowledgeBaseVo>(getKnowledgePage, {
    buildParams: (query) => ({
      name: (query.name as string) || undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { name: '', description: '', status: 1 })
  modalOpen.value = true
}

function openEdit(record: KnowledgeBaseVo) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    description: record.description || '',
    status: record.status,
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.name) {
    message.warning(t('page.aiKnowledgeRequired'))
    return
  }
  saving.value = true
  try {
    const payload: KnowledgeBaseSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateKnowledge(payload)
    } else {
      await createKnowledge(payload)
    }
    message.success(t('page.aiKnowledgeSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: KnowledgeBaseVo) {
  Modal.confirm({
    title: t('page.aiKnowledgeDeleteTitle'),
    content: t('page.aiKnowledgeDeleteConfirm', { name: record.name }),
    onOk: async () => {
      await deleteKnowledge(record.id)
      message.success(t('page.aiKnowledgeDeleted'))
      loadData()
    },
  })
}

// ---------- 文档 ----------

const docsDrawerOpen = ref(false)
const currentBaseId = ref<number | undefined>()
const currentBaseName = ref('')

function openDocs(record: KnowledgeBaseVo) {
  currentBaseId.value = record.id
  currentBaseName.value = record.name
  docsDrawerOpen.value = true
  docLoadData()
}

function closeDocs() {
  docsDrawerOpen.value = false
  currentBaseId.value = undefined
}

const docColumns = [
  { title: t('page.aiKnowledgeDocName'), dataIndex: 'title', key: 'title' },
  { title: t('page.aiKnowledgeStatus'), dataIndex: 'status', key: 'status', width: 90 },
  { title: t('common.actions'), key: 'actions', width: 120 },
]

const docSaving = ref(false)
const docModalOpen = ref(false)
const docEditingId = ref<number | undefined>()
const docForm = reactive<KnowledgeDocSaveRequest>({
  baseId: 0,
  title: '',
  content: '',
  status: 1,
})

const {
  pageNum: docPageNum,
  pageSize: docPageSize,
  total: docTotal,
  loading: docLoading,
  records: docRecords,
  error: docError,
  loadData: docLoadData,
} = useTableQuery<KnowledgeDocVo>(getKnowledgeDocPage, {
  immediate: false,
  buildParams: () => ({
    baseId: currentBaseId.value,
  }),
})

function openDocCreate() {
  docEditingId.value = undefined
  Object.assign(docForm, { baseId: currentBaseId.value, title: '', content: '', status: 1 })
  docModalOpen.value = true
}

function openDocEdit(record: KnowledgeDocVo) {
  docEditingId.value = record.id
  Object.assign(docForm, {
    baseId: record.baseId,
    title: record.title,
    content: record.content || '',
    status: record.status,
  })
  docModalOpen.value = true
}

async function onDocSubmit() {
  if (!docForm.title) {
    message.warning(t('page.aiKnowledgeDocRequired'))
    return
  }
  docSaving.value = true
  try {
    const payload: KnowledgeDocSaveRequest = { ...docForm }
    if (docEditingId.value) {
      payload.id = docEditingId.value
      await updateKnowledgeDoc(payload)
    } else {
      await createKnowledgeDoc(payload)
    }
    message.success(t('page.aiKnowledgeDocSaved'))
    docModalOpen.value = false
    docLoadData()
  } finally {
    docSaving.value = false
  }
}

function onDocDelete(record: KnowledgeDocVo) {
  Modal.confirm({
    title: t('page.aiKnowledgeDocDeleteTitle'),
    content: t('page.aiKnowledgeDocDeleteConfirm', { name: record.title }),
    onOk: async () => {
      await deleteKnowledgeDoc(record.id)
      message.success(t('page.aiKnowledgeDocDeleted'))
      docLoadData()
    },
  })
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
