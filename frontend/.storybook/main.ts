import type { StorybookConfig } from '@storybook/vue3-vite'
import type { Plugin, UserConfig } from 'vite'

/**
 * Storybook 配置：Vue3 + Vite 7 框架。
 *
 * - stories：扫描 src 下的 .stories.ts 与 .mdx，与单测文件（.spec.ts）互不干扰
 * - addons：docs（自动从组件源码生成 Props/Events 文档）+ a11y（可访问性检查）
 * - 项目自身的 vite.config 会自动合并（@ 别名、unplugin 的 antd 组件按需解析），
 *   但 PWA 插件不适合文档站点（无 service worker），在 viteFinal 中剔除
 */
const config: StorybookConfig = {
  stories: ['../src/**/*.mdx', '../src/**/*.stories.@(js|jsx|mjs|ts|tsx)'],
  addons: ['@storybook/addon-docs', '@storybook/addon-a11y'],
  framework: {
    name: '@storybook/vue3-vite',
  },
  core: {
    disableTelemetry: true,
  },
  async viteFinal(config: UserConfig) {
    // 剥离 vite-plugin-pwa：Storybook 文档站不需要离线缓存/Service Worker。
    // VitePWA() 返回的是 5 个插件的数组，会嵌套在 plugins 里，需先拍平再过滤。
    const plugins = (config.plugins || [])
      .flat(Infinity)
      .filter((plugin): plugin is Plugin => {
        const name = (plugin as Plugin | undefined)?.name
        return Boolean(name) && name !== 'vite-plugin-pwa' && !name.startsWith('vite-plugin-pwa:')
      })
    return { ...config, plugins }
  },
}

export default config
