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
        name: '后台管理系统',
        short_name: '后台管理系统',
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
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks: {
          antd: ['ant-design-vue', '@ant-design/icons-vue'],
          // echarts 已按需引入（echarts/core + charts/components/renderers），
          // 手动分块只聚合实际使用的子入口，避免把全量 echarts 打进包
          echarts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
          vendor: ['vue', 'vue-router', 'pinia', 'axios', 'vue-i18n'],
        },
      },
    },
  },
})
