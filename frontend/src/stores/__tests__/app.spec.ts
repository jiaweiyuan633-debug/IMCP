import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'

/**
 * 应用偏好持久化契约测试——暗黑主题从 localStorage 恢复并在切换时写回。
 */
describe('app store 主题持久化', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('默认浅色主题', () => {
    const store = useAppStore()
    expect(store.darkTheme).toBe(false)
  })

  it('toggleTheme 切换主题并持久化到 localStorage', () => {
    const store = useAppStore()
    store.toggleTheme()
    expect(store.darkTheme).toBe(true)
    expect(localStorage.getItem('admin_dark_theme')).toBe('1')

    store.toggleTheme()
    expect(store.darkTheme).toBe(false)
    expect(localStorage.getItem('admin_dark_theme')).toBe('0')
  })

  it('初始化从 localStorage 恢复暗黑主题偏好', () => {
    localStorage.setItem('admin_dark_theme', '1')
    setActivePinia(createPinia())
    const store = useAppStore()
    expect(store.darkTheme).toBe(true)
  })

  it('setDarkTheme 显式设置并写回', () => {
    const store = useAppStore()
    store.setDarkTheme(true)
    expect(store.darkTheme).toBe(true)
    expect(localStorage.getItem('admin_dark_theme')).toBe('1')
  })

  it('locale 依旧读写 admin_locale 键', () => {
    const store = useAppStore()
    store.setLocale('en-US')
    expect(store.locale).toBe('en-US')
    expect(localStorage.getItem('admin_locale')).toBe('en-US')
  })
})
