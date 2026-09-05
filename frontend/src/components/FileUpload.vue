<template>
  <a-upload :show-upload-list="false" :custom-request="doUpload" accept="image/*">
    <a-avatar v-if="value" :src="displayUrl" :size="64" shape="square" />
    <a-button v-else>{{ t('common.uploadTitle') }}</a-button>
  </a-upload>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { uploadFile } from '@/api/common'
import { withFileToken } from '@/utils/fileUrl'
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
  try {
    // 统一走 withFileToken（现取令牌 + origin 拼接），与文件列表/导入导出共享同一实现
    return await withFileToken(url)
  } catch {
    // 令牌签发失败时不再保留无 token 的原 URL：FileAccessFilter 对 /files、/uploads 一律
    // 要求令牌，无令牌请求必然 403，保留只会产生裂图与错误请求
    return ''
  }
}
</script>

