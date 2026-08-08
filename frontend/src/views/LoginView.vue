<template>
  <div class="login-page">
    <a-card title="双端管理脚手架" style="width: 360px">
      <a-form layout="vertical">
        <a-form-item label="账号">
          <a-input placeholder="admin" />
        </a-form-item>
        <a-form-item label="密码">
          <a-input-password placeholder="******" />
        </a-form-item>
        <a-button type="primary" block>登录</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
</style>

