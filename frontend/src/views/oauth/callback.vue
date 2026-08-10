<template>
  <div class="oauth-callback-page">
    <a-spin v-if="processing" size="large" :tip="t('page.oauthProcessing')">
      <div class="spin-placeholder" />
    </a-spin>
    <a-card v-else-if="bindMode" class="bind-card">
      <div class="bind-title">{{ t('page.oauthBindTitle') }}</div>
      <div class="bind-provider">
        <a-tag v-if="providerLabel" color="blue">{{ providerLabel }}</a-tag>
        <a-tag v-else>{{ provider }}</a-tag>
      </div>
      <a-form layout="vertical" :model="bindForm" @finish="onBind">
        <a-form-item
          :label="t('login.username')"
          name="username"
          :rules="[{ required: true, message: t('login.usernamePlaceholder') }]"
        >
          <a-input v-model:value="bindForm.username" :placeholder="t('login.usernamePlaceholder')" />
        </a-form-item>
        <a-form-item
          :label="t('login.password')"
          name="password"
          :rules="[{ required: true, message: t('login.passwordPlaceholder') }]"
        >
          <a-input-password v-model:value="bindForm.password" :placeholder="t('login.passwordPlaceholder')" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="binding">{{ t('page.oauthBindConfirm') }}</a-button>
      </a-form>
      <div class="bind-actions">
        <a @click="goLogin">{{ t('page.oauthBindBackLogin') }}</a>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { bindOauth, consumeOauthTicket } from '@/api/oauth'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const processing = ref(true)
const bindMode = ref(false)
const bindToken = ref('')
const provider = ref('')
const providerLabel = ref('')
const binding = ref(false)
const bindForm = reactive({ username: '', password: '' })

onMounted(async () => {
  const query = route.query as Record<string, string>
  if (query.bound === 'true') {
    message.success(t('page.oauthBoundSuccess'))
    router.replace('/')
    return
  }
  if (query.ticket) {
    try {
      const data = await consumeOauthTicket(query.ticket)
      userStore.applyLogin(data)
      message.success(t('page.oauthLoginSuccess'))
      router.replace('/')
    } catch (error) {
      message.error((error as Error).message || t('page.oauthProcessingFailed'))
      router.replace('/login')
    } finally {
      processing.value = false
    }
    return
  }
  if (query.bindToken) {
    bindToken.value = query.bindToken
    provider.value = query.provider || ''
    providerLabel.value = query.providerLabel || ''
    bindMode.value = true
    processing.value = false
    return
  }
  processing.value = false
  message.error(t('page.oauthCallbackInvalid'))
  router.replace('/login')
})

async function onBind() {
  if (!bindForm.username || !bindForm.password) {
    message.warning(`${t('common.inputPlaceholder')}${t('page.oauthBindAccount')}`)
    return
  }
  binding.value = true
  try {
    const data = await bindOauth({
      bindToken: bindToken.value,
      username: bindForm.username,
      password: bindForm.password,
    })
    userStore.applyLogin(data)
    message.success(t('page.oauthBoundSuccess'))
    router.replace('/')
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    binding.value = false
  }
}

function goLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.oauth-callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.spin-placeholder {
  width: 160px;
  height: 160px;
}

.bind-card {
  width: 360px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

.bind-title {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 12px;
}

.bind-provider {
  text-align: center;
  margin-bottom: 20px;
}

.bind-actions {
  margin-top: 12px;
  text-align: center;
}
</style>
