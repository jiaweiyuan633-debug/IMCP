<template>
  <a-card :title="t('page.monitorCacheTitle')">
    <a-form layout="inline">
      <a-form-item :label="t('page.monitorCacheKey')">
        <a-input v-model:value="key" placeholder="login:token:*" style="width: 320px" />
      </a-form-item>
      <a-form-item>
        <a-button v-permission="'monitor:cache:delete'" type="primary" :loading="deleting" @click="onDelete">
          {{ t('page.monitorClear') }}
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const commonKeys = ['login:token:', 'login:online:', 'auth:perms:', 'ai:task:']
const key = ref('')
const deleting = ref(false)

async function onDelete() {
  if (!key.value) {
    message.warning(`${t('common.inputPlaceholder')}${t('page.monitorCacheKey')}`)
    return
  }
  deleting.value = true
  try {
    await clearCacheKey(key.value)
    message.success(t('page.monitorCacheCleared'))
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

