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
              { min: 6, message: t('page.passwordMin') },
            ]"
          >
            <a-input-password v-model:value="form.newPassword" />
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading">{{ t('page.passwordConfirm') }}</a-button>
        </a-form>
      </a-card>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import FileUpload from '@/components/FileUpload.vue'
import { updateProfile } from '@/api/auth'
import { useI18n } from 'vue-i18n'

const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
const savingProfile = ref(false)
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
</script>

