<template>
  <a-card :title="t('page.aiEnhChatTitle')">
    <a-row :gutter="16">
      <a-col :xs="24" :md="7">
        <a-form layout="vertical">
          <a-form-item :label="t('page.aiEnhService')" required>
            <a-select
              v-model:value="serviceCode"
              :placeholder="t('common.selectPlaceholder')"
              :options="configOptions"
              allow-clear
            />
          </a-form-item>
          <a-form-item :label="t('page.aiEnhModel')">
            <a-input v-model:value="model" :placeholder="`${t('common.inputPlaceholder')}gpt-4o-mini`" />
          </a-form-item>
          <a-form-item :label="t('page.aiEnhTemplate')">
            <a-select v-model:value="templateCode" :options="templateOptions" allow-clear />
          </a-form-item>
          <a-form-item :label="t('page.aiEnhKnowledge')">
            <a-select
              v-model:value="knowledgeBaseId"
              :placeholder="t('page.aiEnhNoKnowledge')"
              :options="knowledgeOptions"
              allow-clear
              :disabled="!useKnowledge"
            />
          </a-form-item>
          <a-form-item>
            <a-space direction="vertical" style="width: 100%">
              <a-switch v-model:checked="useKnowledge" :checked-children="t('page.aiEnhUseKnowledge')" />
              <a-input-number
                v-model:value="topK"
                :min="1"
                :max="20"
                :disabled="!useKnowledge"
                :placeholder="t('page.aiEnhTopK')"
                style="width: 100%"
              />
            </a-space>
          </a-form-item>
        </a-form>
      </a-col>
      <a-col :xs="24" :md="17">
        <div class="chat-body">
          <a-empty v-if="messages.length === 0" :description="t('page.aiEnhChatPlaceholder')" />
          <div v-for="(item, index) in messages" :key="index" class="msg-row" :class="item.role">
            <div class="msg-bubble">
              <div class="msg-content">{{ item.content }}</div>
              <div v-if="item.durationMs != null" class="msg-meta">
                {{ t('page.aiEnhDuration', { ms: item.durationMs }) }}
              </div>
            </div>
          </div>
          <div v-if="thinking" class="msg-row assistant">
            <div class="msg-bubble">
              <a-spin size="small" />
              <span class="msg-content">{{ t('page.aiEnhThinking') }}</span>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <a-textarea
            v-model:value="input"
            :rows="3"
            :placeholder="t('page.aiEnhChatPlaceholder')"
            @keydown.enter.exact.prevent="onSend"
          />
          <a-button type="primary" :loading="thinking" :disabled="thinking" @click="onSend">
            {{ t('page.aiEnhSend') }}
          </a-button>
        </div>
      </a-col>
    </a-row>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { chatAi, getKnowledgeOptions, getPromptPage } from '@/api/aiEnhance'
import type { AiChatMessage } from '@/api/aiEnhance'
import { getAiConfigs } from '@/api/ai'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface ChatItem extends AiChatMessage {
  durationMs?: number
}

const serviceCode = ref<string>()
const model = ref('')
const templateCode = ref<string>()
const knowledgeBaseId = ref<number>()
const useKnowledge = ref(false)
const topK = ref(5)
const input = ref('')
const thinking = ref(false)
const messages = ref<ChatItem[]>([])

const configs = ref<{ id: number; code: string; name: string; model?: string }[]>([])
const templates = ref<{ code: string; name: string }[]>([])
const knowledgeBases = ref<{ id: number; name: string }[]>([])

const configOptions = computed(() =>
  configs.value.map((c) => ({ label: `${c.name} (${c.code})`, value: c.code })),
)
const templateOptions = computed(() => [
  { label: t('page.aiEnhNoTemplate'), value: '' },
  ...templates.value.map((p) => ({ label: p.name, value: p.code })),
])
const knowledgeOptions = computed(() =>
  knowledgeBases.value.map((b) => ({ label: b.name, value: b.id })),
)

onMounted(async () => {
  try {
    configs.value = await getAiConfigs()
    if (configs.value.length > 0 && !serviceCode.value) {
      serviceCode.value = configs.value[0].code
      model.value = configs.value[0].model || ''
    }
  } catch {
    // 忽略初始化失败，用户可手动选择
  }
  try {
    const page = await getPromptPage({ pageNum: 1, pageSize: 100 })
    templates.value = page.records.map((p) => ({ code: p.code, name: p.name }))
  } catch {
    // 忽略
  }
  try {
    knowledgeBases.value = await getKnowledgeOptions()
  } catch {
    // 忽略
  }
})

async function onSend() {
  const text = input.value.trim()
  if (!serviceCode.value) {
    message.warning(t('page.aiEnhNeedService'))
    return
  }
  if (!text) {
    message.warning(t('page.aiEnhNeedMessage'))
    return
  }
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  thinking.value = true
  try {
    const history: AiChatMessage[] = messages.value
      .filter((m) => m.role !== 'system')
      .map((m) => ({ role: m.role, content: m.content }))
    const result = await chatAi({
      serviceCode: serviceCode.value,
      model: model.value || undefined,
      templateCode: templateCode.value || undefined,
      useKnowledge: useKnowledge.value ? true : undefined,
      knowledgeBaseId: useKnowledge.value ? knowledgeBaseId.value : undefined,
      topK: useKnowledge.value ? topK.value : undefined,
      messages: history,
    })
    messages.value.push({
      role: 'assistant',
      content: result.content,
      durationMs: result.durationMs,
    })
  } catch {
    // 请求失败时移除刚追加的用户消息，避免历史误导后续上下文
    messages.value.pop()
  } finally {
    thinking.value = false
  }
}
</script>

<style scoped>
.chat-body {
  height: 420px;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}
.msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f5f5f5;
}
.msg-row.user .msg-bubble {
  background: #1677ff;
  color: #fff;
}
.msg-content {
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-meta {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.6;
}
.msg-row.user .msg-meta {
  text-align: right;
}
.chat-input {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  align-items: flex-end;
}
.chat-input .ant-btn {
  flex-shrink: 0;
}
</style>
