<template>
  <a-row :gutter="16">
    <a-col :xs="24" :lg="10">
      <a-card title="个人信息">
        <FileUpload v-model:value="profileForm.avatar" />
        <a-form layout="vertical" :model="profileForm" style="margin-top: 16px">
          <a-form-item label="昵称"><a-input v-model:value="profileForm.nickname" /></a-form-item>
          <a-form-item label="邮箱"><a-input v-model:value="profileForm.email" /></a-form-item>
          <a-form-item label="手机号"><a-input v-model:value="profileForm.phone" /></a-form-item>
          <a-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</a-button>
        </a-form>
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="账号">{{ userStore.userInfo?.username }}</a-descriptions-item>
          <a-descriptions-item label="角色">{{ rolesText }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
    </a-col>
    <a-col :xs="24" :lg="14">
      <a-card title="修改密码">
        <a-form layout="vertical" :model="form" @finish="onSubmit">
          <a-form-item
            label="原密码"
            name="oldPassword"
            :rules="[{ required: true, message: '请输入原密码' }]"
          >
            <a-input-password v-model:value="form.oldPassword" />
          </a-form-item>
          <a-form-item
            label="新密码"
            name="newPassword"
            :rules="[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码至少 6 位' },
            ]"
          >
            <a-input-password v-model:value="form.newPassword" />
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading">确认修改</a-button>
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

const userStore = useUserStore()
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
    message.success('密码修改成功')
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
    message.success('资料已保存')
  } finally {
    savingProfile.value = false
  }
}
</script>

