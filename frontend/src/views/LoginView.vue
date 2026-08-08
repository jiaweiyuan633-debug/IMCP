<template>
  <div class="login-page">
    <a-card class="login-card">
      <div class="login-title">双端管理脚手架</div>
      <a-form layout="vertical" :model="form" @finish="onSubmit">
        <a-form-item
          label="账号"
          name="username"
          :rules="[{ required: true, message: '请输入账号' }]"
        >
          <a-input v-model:value="form.username" placeholder="admin" />
        </a-form-item>
        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password v-model:value="form.password" placeholder="******" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="loading">登录</a-button>
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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
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
