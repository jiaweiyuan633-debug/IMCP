import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
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
    rollupOptions: {
      output: {
        manualChunks: {
          antd: ['ant-design-vue', '@ant-design/icons-vue'],
          echarts: ['echarts'],
          vendor: ['vue', 'vue-router', 'pinia', 'axios', 'vue-i18n'],
        },
      },
    },
  },
})
