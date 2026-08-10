<template>
  <a-card :title="t('page.mcpTitle')">
    <ProSearchForm :fields="searchFields" :loading="loading" @search="onSearch" @reset="onReset" />
    <div class="toolbar">
      <a-button v-permission="'system:mcp:add'" type="primary" @click="openCreate">
        <PlusOutlined />
        {{ t('page.mcpAdd') }}
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
        <template v-if="column.key === 'enabled'">
          <a-switch
            :checked="record.enabled === 1"
            :disabled="!canChangeStatus"
            @change="(checked: boolean) => onStatusChange(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a v-permission="'system:mcp:list'" @click="openTools(record)">{{ t('page.mcpTools') }}</a>
            <a v-permission="'system:mcp:edit'" @click="openEdit(record)">{{ t('common.edit') }}</a>
            <a v-permission="'system:mcp:delete'" class="danger" @click="onDelete(record)">{{ t('common.delete') }}</a>
          </a-space>
        </template>
      </template>
    </ProTable>

    <ModalForm
      v-model:open="modalOpen"
      :title="editingId ? t('page.mcpEdit') : t('page.mcpAdd')"
      :loading="saving"
      @ok="onSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item :label="t('page.mcpName')" required>
          <a-input v-model:value="form.name" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('page.mcpUrl')" required>
          <a-input v-model:value="form.url" :placeholder="t('page.mcpUrlPlaceholder')" :maxlength="255" />
        </a-form-item>
        <a-form-item :label="t('page.mcpAuthToken')">
          <a-input-password
            v-model:value="form.authToken"
            :placeholder="editingId ? t('page.mcpTokenEditPlaceholder') : t('page.mcpTokenPlaceholder')"
            :maxlength="255"
          />
          <div v-if="editingId && hasToken" class="token-hint">{{ t('page.mcpTokenHint') }}</div>
        </a-form-item>
        <a-form-item :label="t('page.mcpStatus')">
          <a-select v-model:value="form.enabled" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('page.mcpSort')">
          <a-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </a-form-item>
        <a-form-item :label="t('page.mcpRemark')">
          <a-textarea v-model:value="form.remark" :rows="3" :maxlength="255" />
        </a-form-item>
      </a-form>
    </ModalForm>

    <a-drawer
      v-model:open="toolsDrawerOpen"
      :title="`${t('page.mcpTools')} · ${currentServer?.name || ''}`"
      :width="560"
    >
      <a-spin :spinning="toolsLoading">
        <a-alert v-if="toolsError" type="error" :message="toolsError" show-icon style="margin-bottom: 16px" />
        <a-empty v-else-if="!toolsList.length" :description="t('page.mcpToolsEmpty')" />
        <a-list v-else size="small" :data-source="toolsList">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <a-space>
                    <a-tag color="blue">{{ item.name }}</a-tag>
                    <span>{{ item.title }}</span>
                  </a-space>
                </template>
                <template #description>{{ item.description }}</template>
              </a-list-item-meta>
              <a-button size="small" type="primary" @click="openCall(item)">{{ t('page.mcpCall') }}</a-button>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="callModalOpen"
      :title="`${t('page.mcpCall')} · ${currentTool?.name || ''}`"
      :confirm-loading="calling"
      :footer="null"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('page.mcpCallArgs')">
          <a-textarea v-model:value="callArgsJson" :rows="4" :placeholder="t('page.mcpCallArgsPlaceholder')" />
        </a-form-item>
      </a-form>
      <a-button type="primary" :loading="calling" block @click="onCall">
        {{ t('page.mcpCall') }}
      </a-button>
      <template v-if="callResult">
        <a-divider />
        <a-alert
          :type="callResult.isError ? 'error' : 'success'"
          :message="callResult.isError ? t('page.mcpCallError') : t('page.mcpCallSuccess')"
          show-icon
          style="margin-bottom: 8px"
        />
        <pre class="call-result">{{ callResultText }}</pre>
      </template>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ProSearchForm from '@/components/ProSearchForm.vue'
import ProTable from '@/components/ProTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { useTableQuery } from '@/composables/useTableQuery'
import { usePermissionStore } from '@/stores/permission'
import {
  callMcpTool,
  createMcpServer,
  deleteMcpServer,
  getMcpServerPage,
  getMcpServerTools,
  updateMcpServer,
  updateMcpServerStatus,
} from '@/api/mcp'
import type { McpCallResultVo, McpServerSaveRequest, McpServerVo, McpToolVo } from '@/api/mcp'
import type { SearchField } from '@/types'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canChangeStatus = computed(() => permissionStore.perms.includes('system:mcp:status'))

const statusOptions = [
  { label: t('page.mcpEnabled'), value: 1 },
  { label: t('page.mcpDisabled'), value: 0 },
]

const searchFields: SearchField[] = [
  {
    label: t('page.mcpName'),
    prop: 'keyword',
  },
  {
    label: t('page.mcpStatus'),
    prop: 'enabled',
    type: 'select',
    options: statusOptions,
  },
]

const columns = [
  { title: t('page.mcpName'), dataIndex: 'name', key: 'name', width: 180 },
  { title: t('page.mcpUrl'), dataIndex: 'url', key: 'url', ellipsis: true },
  { title: t('page.mcpStatus'), dataIndex: 'enabled', key: 'enabled', width: 90 },
  { title: t('page.mcpSort'), dataIndex: 'sort', key: 'sort', width: 70 },
  { title: t('page.mcpRemark'), dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: t('common.actions'), key: 'actions', width: 170 },
]

const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number | undefined>()
const hasToken = ref(false)
const form = reactive<McpServerSaveRequest>({
  name: '',
  url: '',
  authToken: '',
  enabled: 1,
  sort: 0,
  remark: '',
})

const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
  useTableQuery<McpServerVo>(getMcpServerPage, {
    buildParams: (query) => ({
      keyword: (query.keyword as string) || undefined,
      enabled: query.enabled as number | undefined,
    }),
  })

function openCreate() {
  editingId.value = undefined
  hasToken.value = false
  Object.assign(form, { name: '', url: '', authToken: '', enabled: 1, sort: 0, remark: '' })
  modalOpen.value = true
}

function openEdit(record: McpServerVo) {
  editingId.value = record.id
  hasToken.value = !!record.hasAuthToken
  Object.assign(form, {
    name: record.name,
    url: record.url,
    authToken: '',
    enabled: record.enabled,
    sort: record.sort,
    remark: record.remark || '',
  })
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.name || !form.url) {
    message.warning(t('page.mcpRequired'))
    return
  }
  saving.value = true
  try {
    const payload: McpServerSaveRequest = { ...form }
    if (editingId.value) {
      payload.id = editingId.value
      await updateMcpServer(payload)
    } else {
      await createMcpServer(payload)
    }
    message.success(t('page.mcpSaved'))
    modalOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function onStatusChange(record: McpServerVo, checked: boolean) {
  const next = checked ? 1 : 0
  const previous = record.enabled
  record.enabled = next
  try {
    await updateMcpServerStatus(record.id, next)
  } catch {
    record.enabled = previous
  }
}

function onDelete(record: McpServerVo) {
  Modal.confirm({
    title: t('page.mcpDeleteTitle'),
    content: t('page.mcpDeleteConfirm'),
    onOk: async () => {
      await deleteMcpServer(record.id)
      message.success(t('page.mcpDeleted'))
      loadData()
    },
  })
}

// ---------- 工具浏览与调用 ----------

const currentServer = ref<McpServerVo>()
const toolsDrawerOpen = ref(false)
const toolsLoading = ref(false)
const toolsError = ref('')
const toolsList = ref<McpToolVo[]>([])

async function openTools(record: McpServerVo) {
  currentServer.value = record
  toolsDrawerOpen.value = true
  toolsError.value = ''
  toolsList.value = []
  toolsLoading.value = true
  try {
    toolsList.value = await getMcpServerTools(record.id)
  } catch (e) {
    toolsError.value = (e as Error).message || t('page.mcpToolsLoadError')
  } finally {
    toolsLoading.value = false
  }
}

const currentTool = ref<McpToolVo>()
const callModalOpen = ref(false)
const calling = ref(false)
const callArgsJson = ref('')
const callResult = ref<McpCallResultVo>()

function openCall(tool: McpToolVo) {
  currentTool.value = tool
  callArgsJson.value = ''
  callResult.value = undefined
  callModalOpen.value = true
}

const callResultText = computed(() => {
  if (!callResult.value) {
    return ''
  }
  if (callResult.value.content && callResult.value.content.length) {
    return callResult.value.content.join('\n')
  }
  return JSON.stringify(callResult.value.structuredContent || '', null, 2)
})

async function onCall() {
  if (!currentServer.value || !currentTool.value) {
    return
  }
  let args: Record<string, unknown> | undefined
  if (callArgsJson.value.trim()) {
    try {
      args = JSON.parse(callArgsJson.value)
    } catch {
      message.warning(t('page.mcpCallArgsError'))
      return
    }
  }
  calling.value = true
  try {
    callResult.value = await callMcpTool(currentServer.value.id, {
      toolName: currentTool.value.name,
      arguments: args,
    })
  } catch (e) {
    callResult.value = { isError: true, content: [(e as Error).message || t('page.mcpCallError')] }
  } finally {
    calling.value = false
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
.token-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #52c41a;
}
.call-result {
  max-height: 320px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
