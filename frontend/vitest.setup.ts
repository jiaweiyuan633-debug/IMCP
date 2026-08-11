import { vi } from 'vitest'

/**
 * Vitest 全局环境补齐（jsdom 缺失的浏览器 API）。
 * antd-vue 组件渲染依赖 matchMedia 与 ResizeObserver。
 */

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

window.ResizeObserver = window.ResizeObserver || (ResizeObserverMock as unknown as typeof ResizeObserver)

// antd message 提示依赖 getComputedStyle，jsdom 已内置；此处仅兜底滚动/布局相关缺口
Object.defineProperty(window.HTMLElement.prototype, 'scrollIntoView', {
  writable: true,
  value: vi.fn(),
})
