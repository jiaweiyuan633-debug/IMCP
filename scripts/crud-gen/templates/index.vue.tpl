<template>
  <a-card title="{{comment}}管理">
    <ProSearchForm
      :fields="searchFields"
      :loading="loading"
      @search="onSearch"
      @reset="onReset"
    />
    <div class="toolbar">
      <a-button v-permission="'{{permPrefix}}:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        新增{{comment}}
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
        <template v-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'{{permPrefix}}:edit'" @click="openEdit(record)">编辑</a>
            <a v-permission="'{{permPrefix}}:delete'" class="danger" @click="onDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? '编辑{{comment}}' : '新增{{comment}}'"
      :loading="saving"
      :width="560"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
[[for:form_items]]{{item}}
[[/for]]      </a-form>
    </ModalForm>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import {
  create{{Entity}},
  delete{{Entity}},
  get{{Entity}}Page,
  update{{Entity}},
} from '@/api/{{module}}'
import type { {{Entity}}SaveRequest, {{Entity}}Vo } from '@/api/{{module}}'
import type { SearchField } from '@/types'
import { dateColumn } from '@/utils/table'

const searchFields: SearchField[] = [
[[for:search_fields]]{{item}}
[[/for]]
]

const columns = [
[[for:columns]]{{item}}
[[/for]]
  dateColumn('createdAt', { title: '创建时间', width: 170 }),
  { title: '操作', key: 'actions', width: 150 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const form = reactive<{{Entity}}SaveRequest>({})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<{{Entity}}Vo>(get{{Entity}}Page, {
    buildParams: (query) => ({
[[for:param_fields]]      {{item}}
[[/for]]    }),
  })

function openCreate() {
  editingId.value = undefined
  Object.keys(form).forEach((k) => delete form[k as keyof {{Entity}}SaveRequest])
  modalOpen.value = true
}

function openEdit(record: {{Entity}}Vo) {
  editingId.value = record.id
  Object.assign(form, record)
  modalOpen.value = true
}

async function onSubmit() {
  saving.value = true
  try {
    if (editingId.value) {
      await update{{Entity}}({ ...form, id: editingId.value } as {{Entity}}SaveRequest)
    } else {
      await create{{Entity}}(form as {{Entity}}SaveRequest)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function onDelete(record: {{Entity}}Vo) {
  Modal.confirm({
    title: '删除确认',
    content: `确定删除该{{comment}}？`,
    onOk: async () => {
      await delete{{Entity}}(record.id)
      message.success('删除成功')
      loadData()
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
