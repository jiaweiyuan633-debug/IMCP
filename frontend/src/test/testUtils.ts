import { mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import i18n from '@/locales'

/**
 * 组件测试统一挂载工具：注入与生产一致的 i18n + Pinia 插件。
 *
 * 用法：
 * ```ts
 * const wrapper = mountWithPlugins(StatusTag, { props: { value: '1' } })
 * ```
 * 如需额外插件/挂载选项，通过 options.global / options 透传。
 *
 * 返回 VueWrapper<any>：测试中可直接访问 setup 返回的任意 ref/函数（vm.records 等）。
 */
export function mountWithPlugins(component: unknown, options: Record<string, unknown> = {}) {
  const global = (options.global as Record<string, unknown>) || {}
  return mount(component as never, {
    ...options,
    global: {
      ...global,
      plugins: [...(((global.plugins as never[]) || [])), i18n, createPinia()],
    },
    // 测试需读取任意 setup 返回值（vm.records / vm.onSearch 等），用 any 是合理场景
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  }) as unknown as VueWrapper<any>
}

/** 归一化按钮文案：antd Button 会在两个汉字间插入空格，比较前去掉所有空白。 */
export function normalizeText(text: string): string {
  return text.replace(/\s+/g, '')
}

/** 在组件内查找文案匹配的按钮（忽略 antd 插入的空格）。 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function findButtonByText(wrapper: VueWrapper<any>, text: string) {
  return wrapper
    .findAll('button')
    .find((b) => normalizeText(b.text()).includes(normalizeText(text)))
}
