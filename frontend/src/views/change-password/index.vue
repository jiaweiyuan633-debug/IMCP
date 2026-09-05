<template>
  <div class="change-password-page">
    <a-card class="change-password-card" :title="t('page.forcePasswordTitle')">
      <a-alert
        v-if="expired"
        type="warning"
        :message="t('page.forcePasswordExpiredHint')"
        style="margin-bottom: 16px"
      />
      <a-alert
        v-else
        type="warning"
        :message="t('page.forcePasswordHint')"
        style="margin-bottom: 16px"
      />
      <a-form layout="vertical" :model="form" @finish="onSubmit">
        <a-form-item
          :label="t('page.oldPassword')"
          name="oldPassword"
          :rules="[{ required: true, message: t('page.oldPasswordRequired') }]"
        >
          <a-input-password v-model:value="form.oldPassword" />
        </a-form-item>
        <a-form-item
          :label="t('page.newPassword')"
          name="newPassword"
          :rules="[
            { required: true, message: t('page.newPasswordRequired') },
            { min: 8, message: t('page.passwordMin') },
            { pattern: PASSWORD_PATTERN, message: t('page.passwordPolicy') },
          ]"
        >
          <a-input-password v-model:value="form.newPassword" />
        </a-form-item>
        <a-form-item
          :label="t('page.confirmPassword')"
          name="confirmPassword"
          :rules="[
            { required: true, message: t('page.confirmPasswordRequired') },
            { validator: validateConfirm },
          ]"
        >
          <a-input-password v-model:value="form.confirmPassword" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="loading">{{ t('page.passwordConfirm') }}</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { PASSWORD_PATTERN } from '@/utils/validation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
// 过期改密与首登改密提示差异：?expired=1 由路由守卫在密码过期场景带入
const expired = ref(route.query.expired === '1')

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function validateConfirm(_rule: unknown, value: string): Promise<void> {
  return value === form.newPassword
    ? Promise.resolve()
    : Promise.reject(new Error(t('page.confirmPasswordMismatch')))
}

onMounted(() => {
  document.title = t('page.forcePasswordTitle')
  // 直接访问改密页但未登录 → 回登录页
  if (!userStore.isLoggedIn) {
    router.replace('/login')
  }
})

async function onSubmit() {
  loading.value = true
  try {
    await userStore.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    message.success(t('page.passwordChanged'))
    router.replace('/')
  } catch {
    // error message handled by request layer
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.change-password-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.change-password-card {
  width: 420px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}
</style>
