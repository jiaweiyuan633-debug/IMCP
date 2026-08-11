import type { Preview } from '@storybook/vue3'
import { setup } from '@storybook/vue3'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import i18n from '../src/locales'
import 'ant-design-vue/dist/reset.css'
import '../src/styles/global.css'

/**
 * Storybook 全局环境：为所有 story 安装与生产一致的应用级插件。
 *
 * 组件内部会调用 useI18n() / usePermissionStore() / useRouter()，
 * 这里统一注册 i18n、Pinia 与一个空路由，避免每个 story 重复配置。
 */
setup((app) => {
  app.use(i18n)
  app.use(createPinia())
  app.use(
    createRouter({
      history: createWebHashHistory(),
      routes: [],
    }),
  )
})

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    // 组件本身有背景/暗黑模式，禁用内置背景色切换以免干扰
    backgrounds: { disable: true },
  },
}

export default preview
