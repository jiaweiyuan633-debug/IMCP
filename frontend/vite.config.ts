import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [AntDesignVueResolver({ importStyle: false })],
      dts: false,
    }),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: '智能管理平台',
        short_name: '智能管理平台',
        start_url: '/',
        display: 'standalone',
        theme_color: '#2563eb',
        icons: [{ src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml' }],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico}'],
        navigateFallback: '/index.html',
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true,
    port: 5173,
  },
  build: {
    // 移除 manualChunks 手动分块：脚手架依赖 Vite 默认 chunk 策略
    // （按动态 import 拆分 + 共享依赖自动提取），避免大块 vendor 缓存粒度差与过度拆分
    // echarts canvas 渲染器已按需动态拆分为独立 chunk；阈值按 antd-vue 按需后静态基线调高
    chunkSizeWarningLimit: 1100,
  },
})
