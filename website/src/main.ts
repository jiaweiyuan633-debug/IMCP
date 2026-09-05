import { createApp } from 'vue'
import App from './App.vue'
import './styles.css'
import { registerSW } from 'virtual:pwa-register'
import { initAnalytics } from './analytics'

registerSW({ immediate: true })
// 埋点初始化：未配置 VITE_GA_ID/VITE_GTM_ID 时内部保持 no-op
initAnalytics()

createApp(App).mount('#app')
