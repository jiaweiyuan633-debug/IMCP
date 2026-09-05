import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  // 与生产一致：vue SFC 编译 + antd 组件按需解析（<a-table> 等可在 jsdom 中真实渲染）
  plugins: [
    vue(),
    Components({
      resolvers: [AntDesignVueResolver({ importStyle: false })],
      dts: false,
    }),
  ],
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary'],
      // R4-1.32：门槛上调（原 20/10/10/20 形同虚设）——request 拦截器、auth/system
      // API 契约测试落地后实测 lines 36.5 / functions 27.0 / branches 35.4 / statements 36.3，
      // 门槛保留 ~4 点安全余量防测试抖动破门，后续批次逐步抬升
      thresholds: {
        lines: 32,
        functions: 23,
        branches: 31,
        statements: 32,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
