<template>
  <a-upload :show-upload-list="false" :custom-request="doUpload" accept="image/*">
    <a-avatar v-if="value" :src="displayUrl" :size="64" shape="square" />
    <a-button v-else>选择图片</a-button>
  </a-upload>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { uploadFile } from '@/api/common'

const props = defineProps<{
  value?: string
}>()

const emit = defineEmits<{
  'update:value': [value: string]
}>()

const displayUrl = computed(() => resolveUrl(props.value || ''))

async function doUpload({ file }: { file: File }) {
  try {
    const result = await uploadFile(file)
    emit('update:value', result.url)
    message.success('上传成功')
  } catch {
    message.error('上传失败')
  }
}

function resolveUrl(url: string): string {
  if (!url || url.startsWith('http')) {
    return url
  }
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
  if (base.startsWith('http')) {
    return `${new URL(base).origin}${url}`
  }
  return url
}
</script>

