<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider :width="220" theme="dark">
      <div class="app-logo">双端管理脚手架</div>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="app-header">管理后台</a-layout-header>
      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.app-logo {
  height: 48px;
  line-height: 48px;
  color: #fff;
  text-align: center;
  font-weight: 600;
}

.app-header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.app-content {
  padding: 24px;
}
</style>

