import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
import 'ant-design-vue/dist/reset.css'
import '@/styles/global.css'
import App from './App.vue'
import router from './router'
import { permission } from '@/directives/permission'
import i18n from '@/locales'
import { queryClient } from '@/queryClient'
import { initAuthSync } from '@/utils/auth'
import { registerSW } from 'virtual:pwa-register'

registerSW({ immediate: true })
// 跨标签页凭证同步（其他标签页登出时本标签页同步失效）
initAuthSync()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(VueQueryPlugin, { queryClient })
app.directive('permission', permission)
app.mount('#app')
