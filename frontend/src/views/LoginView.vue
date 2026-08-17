<template>
  <div class="login-page">
    <a-card class="login-card">
      <div class="login-title">{{ t('app.platform') }}</div>
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
        <a-form-item :label="t('login.tenant')" name="tenantId">
          <a-input-number
            v-model:value="form.tenantId"
            :placeholder="t('login.tenantPlaceholder')"
            :min="1"
            :precision="0"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item v-if="captchaEnabled" :label="t('login.captcha')" name="captchaCode">
          <a-space>
            <a-input v-model:value="form.captchaCode" :placeholder="t('login.captchaPlaceholder')" style="width: 160px" />
            <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="captcha-image" @click="loadCaptcha" />
          </a-space>
        </a-form-item>
        <a-form-item v-if="totpRequired" :label="t('login.totp')" name="totpCode">
          <a-input v-model:value="form.totpCode" :placeholder="t('login.totpPlaceholder')" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="loading">{{ t('login.submit') }}</a-button>
      </a-form>
      <template v-if="providers.length">
        <a-divider plain>{{ t('login.oauthDivider') }}</a-divider>
        <div class="oauth-login">
          <a-tag
            v-for="entry in providers"
            :key="entry.provider"
            class="oauth-entry"
            :color="providerColor(entry.provider)"
            @click="onOauth(entry)"
          >
            {{ entry.label }}
          </a-tag>
        </div>
      </template>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import type { LoginForm } from '@/types'
import { useI18n } from 'vue-i18n'
import { getCaptcha, getLoginConfig } from '@/api/auth'
import { getOauthAuthorizeUrl, getOauthProviders } from '@/api/oauth'
import type { OauthProviderVo } from '@/api/oauth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
// R4-1.47（批次1）：移除预填 admin——生产包内置默认管理员名会辅助弱口令爆破
const form = reactive<LoginForm>({
  username: '',
  password: '',
  tenantId: undefined,
})
const captchaEnabled = ref(false)
const captchaImage = ref('')
const totpRequired = ref(false)
const providers = ref<OauthProviderVo[]>([])

async function loadCaptcha() {
  const data = await getCaptcha()
  form.captchaId = data.captchaId
  captchaImage.value = data.image
}

async function loadProviders() {
  try {
    providers.value = await getOauthProviders()
  } catch {
    providers.value = []
  }
}

function providerColor(provider: string): string {
  if (provider === 'wechat') {
    return 'green'
  }
  if (provider === 'github') {
    return 'geekblue'
  }
  return 'red'
}

async function onOauth(entry: OauthProviderVo) {
  try {
    const { url } = await getOauthAuthorizeUrl({ provider: entry.provider })
    window.location.href = url
  } catch (error) {
    message.error((error as Error).message || t('login.oauthFailed'))
  }
}

onMounted(async () => {
  document.title = t('app.platform')
  const config = await getLoginConfig()
  captchaEnabled.value = config.captchaEnabled
  if (config.captchaEnabled) {
    await loadCaptcha()
  }
  loadProviders()
})

async function onSubmit() {
  loading.value = true
  try {
    const data = await userStore.login(form)
    // R4-1.47（批次1）：默认口令/密码过期必须强制改密——登录成功但标记 mustChangePassword
    if (data.mustChangePassword || data.user?.mustChangePassword) {
      router.push('/change-password')
      return
    }
    const rawRedirect = route.query.redirect
    // R4-1.47（批次1）：redirect 仅允许站内路径（单斜杠开头且非 // 协议相对 URL），
    // 防止登录回跳被构造为外部地址/异常路径
    const redirect =
      typeof rawRedirect === 'string' && rawRedirect.startsWith('/') && !rawRedirect.startsWith('//')
        ? rawRedirect
        : '/'
    router.push(redirect)
  } catch (error) {
    if ((error as Error & { code?: number }).code === 1015) {
      totpRequired.value = true
      message.warning(t('login.totpRequired'))
    } else {
      message.error((error as Error).message || t('login.failed'))
    }
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

.captcha-image {
  height: 32px;
  cursor: pointer;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}

.oauth-login {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.oauth-entry {
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 16px;
}
</style>
