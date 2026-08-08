<template>
  <a-card title="缓存管理">
    <a-form layout="inline">
      <a-form-item label="缓存 Key">
        <a-input v-model:value="key" placeholder="例如 login:token:*" style="width: 320px" />
      </a-form-item>
      <a-form-item>
        <a-button v-permission="'monitor:cache:delete'" type="primary" :loading="deleting" @click="onDelete">
          删除缓存
        </a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <a-space wrap>
      <a-tag v-for="item in commonKeys" :key="item" class="cache-key" @click="key = item">{{ item }}</a-tag>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { clearCacheKey } from '@/api/monitor'

const commonKeys = ['login:token:', 'login:online:', 'auth:perms:', 'ai:task:']
const key = ref('')
const deleting = ref(false)

async function onDelete() {
  if (!key.value) {
    message.warning('请输入缓存 Key')
    return
  }
  deleting.value = true
  try {
    await clearCacheKey(key.value)
    message.success('缓存已删除')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.cache-key {
  cursor: pointer;
}
</style>

