<template>
  <a-row :gutter="16">
    <a-col :xs="24" :lg="10">
      <a-card :title="t('page.profileTitle')">
        <FileUpload v-model:value="profileForm.avatar" />
        <a-form layout="vertical" :model="profileForm" style="margin-top: 16px">
          <a-form-item :label="t('page.profileNickname')"><a-input v-model:value="profileForm.nickname" /></a-form-item>
          <a-form-item :label="t('page.profileEmail')"><a-input v-model:value="profileForm.email" /></a-form-item>
          <a-form-item :label="t('page.profilePhone')"><a-input v-model:value="profileForm.phone" /></a-form-item>
          <a-button type="primary" :loading="savingProfile" @click="saveProfile">{{ t('page.profileSave') }}</a-button>
        </a-form>
        <a-descriptions :column="1" bordered>
          <a-descriptions-item :label="t('page.profileAccount')">{{ userStore.userInfo?.username }}</a-descriptions-item>
          <a-descriptions-item :label="t('page.profileRoles')">{{ rolesText }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
    </a-col>
    <a-col :xs="24" :lg="14">
      <a-card :title="t('page.passwordTitle')">
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
            ]"
          >
            <a-input-password v-model:value="form.newPassword" />
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading">{{ t('page.passwordConfirm') }}</a-button>
        </a-form>
      </a-card>
      <a-card :title="t('page.totpTitle')" style="margin-top: 16px">
        <a-space>
          <a-tag :color="totpStatus.enabled ? 'green' : 'default'">
            {{ totpStatus.enabled ? t('page.totpEnabled') : t('page.totpDisabled') }}
          </a-tag>
          <a-button v-if="!totpStatus.enabled" @click="onSetupTotp">{{ t('page.totpSetup') }}</a-button>
          <a-button v-else danger @click="openDisableTotp">{{ t('page.totpDisable') }}</a-button>
        </a-space>
      </a-card>
    </a-col>
  </a-row>

  <a-modal v-model:open="totpModalOpen" :title="t('page.totpTitle')" :confirm-loading="totpSaving" @ok="onTotpSubmit">
    <a-form layout="vertical" :model="totpForm">
      <template v-if="!totpStatus.enabled">
        <a-form-item :label="t('page.totpSecretLabel')">
          <a-input v-model:value="totpForm.secret" read-only />
        </a-form-item>
        <a-form-item :label="t('page.totpUrlLabel')">
          <a-input v-model:value="totpForm.otpauthUrl" read-only />
        </a-form-item>
      </template>
      <a-form-item :label="t('page.totpCodeLabel')" required>
        <a-input v-model:value="totpForm.code" maxlength="6" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import FileUpload from '@/components/FileUpload.vue'
import { disableTotp, enableTotp, getTotpStatus, setupTotp, updateProfile } from '@/api/auth'
import { useI18n } from 'vue-i18n'

const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
const savingProfile = ref(false)
const totpSaving = ref(false)
const totpModalOpen = ref(false)
const totpStatus = reactive({ enabled: false })
const totpForm = reactive({ secret: '', otpauthUrl: '', code: '' })
const form = reactive({
  oldPassword: '',
  newPassword: '',
})
const profileForm = reactive({
  nickname: '',
  avatar: '',
  email: '',
  phone: '',
})

if (userStore.userInfo) {
  profileForm.nickname = userStore.userInfo.nickname || ''
  profileForm.avatar = userStore.userInfo.avatar || ''
  profileForm.email = ''
  profileForm.phone = ''
}

const rolesText = computed(() => (userStore.userInfo?.roles || []).join(', ') || '-')

async function onSubmit() {
  loading.value = true
  try {
    await userStore.changePassword(form)
    message.success(t('page.passwordChanged'))
    form.oldPassword = ''
    form.newPassword = ''
  } catch {
    // error message handled by request layer
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await updateProfile(profileForm)
    await userStore.fetchMe()
    message.success(t('page.profileSaved'))
  } finally {
    savingProfile.value = false
  }
}

async function onSetupTotp() {
  const data = await setupTotp()
  totpForm.secret = data.secret || ''
  totpForm.otpauthUrl = data.otpauthUrl || ''
  totpForm.code = ''
  totpModalOpen.value = true
}

function openDisableTotp() {
  totpForm.secret = ''
  totpForm.otpauthUrl = ''
  totpForm.code = ''
  totpModalOpen.value = true
}

async function onTotpSubmit() {
  if (!totpForm.code) {
    message.warning(`${t('common.inputPlaceholder')}${t('page.totpCodeLabel')}`)
    return
  }
  totpSaving.value = true
  try {
    if (totpStatus.enabled) {
      await disableTotp(totpForm.code)
    } else {
      await enableTotp(totpForm.code)
    }
    totpStatus.enabled = !totpStatus.enabled
    totpModalOpen.value = false
    message.success(t('page.totpSaved'))
  } finally {
    totpSaving.value = false
  }
}

onMounted(async () => {
  const status = await getTotpStatus()
  totpStatus.enabled = status.enabled
})
</script>

