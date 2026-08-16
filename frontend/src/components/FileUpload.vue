<template>
  <a-upload :show-upload-list="false" :custom-request="doUpload" accept="image/*">
    <a-avatar v-if="value" :src="displayUrl" :size="64" shape="square" />
    <a-button v-else>{{ t('common.uploadTitle') }}</a-button>
  </a-upload>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getFileAccessToken, uploadFile } from '@/api/common'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  value?: string
}>()

const emit = defineEmits<{
  'update:value': [value: string]
}>()
const { t } = useI18n()

const displayUrl = ref('')

watch(
  () => props.value,
  async (value) => {
    displayUrl.value = await resolveUrl(value || '')
  },
  { immediate: true },
)

async function doUpload({ file }: { file: File }) {
  try {
    const result = await uploadFile(file)
    emit('update:value', result.url)
    message.success(t('common.uploadSuccess'))
  } catch {
    message.error(t('common.uploadError'))
  }
}

async function resolveUrl(url: string): Promise<string> {
  if (!url || url.startsWith('http')) {
    return url
  }
  try {
    const token = await getFileAccessToken(url)
    url = `${url}?token=${encodeURIComponent(token)}`
  } catch {
    // 令牌签发失败时不再保留无 token 的原 URL：FileAccessFilter 对 /files、/uploads 一律
    // 要求令牌，无令牌请求必然 403，保留只会产生裂图与错误请求（R4-1.42）
    return ''
  }
  // 未注入时默认同源 /api（contentUrl 为 /files/xxx，经 Ingress /files 反代到后端）；
  // 注入绝对地址时取 origin 拼接（独立部署直连后端）
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  if (base.startsWith('http')) {
    return `${new URL(base).origin}${url}`
  }
  return url
}
</script>

