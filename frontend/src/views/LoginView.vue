<template>
  <div class="login-page">
    <a-card class="login-card">
      <div class="login-title">{{ t('app.title') }}</div>
      <a-form layout="vertical" :model="form" @finish="onSubmit">
        <a-form-item
          :label="t('login.username')"
          name="username"
          :rules="[{ required: true, message: t('login.usernamePlaceholder') }]"
        >
          <a-input v-model:value="form.username" :placeholder="t('login.usernamePlaceholder')" />
        </a-form-item>
        <a-form-item
          :label="t('login.password')"
          name="password"
          :rules="[{ required: true, message: t('login.passwordPlaceholder') }]"
        >
          <a-input-password v-model:value="form.password" :placeholder="t('login.passwordPlaceholder')" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="loading">{{ t('login.submit') }}</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import type { LoginForm } from '@/types'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
const form = reactive<LoginForm>({
  username: 'admin',
  password: '',
})

async function onSubmit() {
  loading.value = true
  try {
    await userStore.login(form)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (error) {
    message.error((error as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.login-card {
  width: 360px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 20px;
}
</style>
