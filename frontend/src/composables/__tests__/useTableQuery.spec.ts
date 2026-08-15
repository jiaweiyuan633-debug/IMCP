import { defineComponent } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { mountWithPlugins } from '@/test/testUtils'
import { useTableQuery } from '@/composables/useTableQuery'
import type { PageResult } from '@/types'

interface Demo {
  id: number
  name: string
}

function page(records: Demo[], total: number, pageNum: number, pageSize: number): PageResult<Demo> {
  return { records, total, pageNum, pageSize }
}

function mountHarness(
  fetcher: (params: unknown, signal?: AbortSignal) => Promise<PageResult<Demo>>,
  options?: unknown,
) {
  const Harness = defineComponent({
    setup() {
      const { pageNum, pageSize, total, loading, records, searchModel, error, loadData, onSearch, onReset } =
        useTableQuery<Demo>(fetcher as never, options as never)
      return { pageNum, pageSize, total, loading, records, searchModel, error, loadData, onSearch, onReset }
    },
    template: '<div />',
  })
  return mountWithPlugins(Harness)
}

describe('useTableQuery', () => {
  it('挂载后自动加载首屏数据', async () => {
    const fetcher = vi.fn().mockResolvedValue(page([{ id: 1, name: 'a' }], 1, 1, 10))
    const wrapper = mountHarness(fetcher)
    expect(wrapper.vm.loading).toBe(true)
    await flushPromises()
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.records).toEqual([{ id: 1, name: 'a' }])
    expect(wrapper.vm.total).toBe(1)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('onSearch 合并搜索模型并重置到第一页', async () => {
    const fetcher = vi.fn().mockResolvedValue(page([], 0, 1, 10))
    const wrapper = mountHarness(fetcher)
    await flushPromises()
    wrapper.vm.onSearch({ keyword: 'admin' })
    await flushPromises()
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: 'admin', pageNum: 1, pageSize: 10 }, expect.any(AbortSignal))
  })

  it('buildParams 映射查询参数', async () => {
    const fetcher = vi.fn().mockResolvedValue(page([], 0, 1, 10))
    const wrapper = mountHarness(fetcher, {
      buildParams: (query: Record<string, unknown>) => ({ keyword: (query.k as string) || '' }),
    })
    await flushPromises()
    wrapper.vm.onSearch({ k: '设备' })
    await flushPromises()
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '设备', pageNum: 1, pageSize: 10 }, expect.any(AbortSignal))
  })

  it('onReset 清空搜索条件后重新加载', async () => {
    const fetcher = vi.fn().mockResolvedValue(page([], 0, 1, 10))
    const wrapper = mountHarness(fetcher, {
      buildParams: (query: Record<string, unknown>) => ({ keyword: (query.k as string) || '' }),
    })
    await flushPromises()
    wrapper.vm.onSearch({ k: '设备' })
    await flushPromises()
    wrapper.vm.onReset()
    await flushPromises()
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '', pageNum: 1, pageSize: 10 }, expect.any(AbortSignal))
  })

  it('请求失败时记录 error 并清空数据', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('超时'))
    const wrapper = mountHarness(fetcher)
    await flushPromises()
    expect(wrapper.vm.error?.message).toBe('超时')
    expect(wrapper.vm.records).toEqual([])
    expect(wrapper.vm.total).toBe(0)
  })

  it('immediate:false 时不自动加载', async () => {
    const fetcher = vi.fn().mockResolvedValue(page([], 0, 1, 10))
    mountHarness(fetcher, { immediate: false })
    await flushPromises()
    expect(fetcher).not.toHaveBeenCalled()
  })

  it('迟到的旧请求不覆盖更新的结果（竞态守卫）', async () => {
    // 模拟快速翻页：请求1（第1页）挂起，请求2（第2页）后发起但先返回，
    // 请求1 若在请求2 之后返回会覆盖用户看到的第2页数据
    let resolveFirst!: (v: PageResult<Demo>) => void
    let resolveSecond!: (v: PageResult<Demo>) => void
    const first = new Promise<PageResult<Demo>>((resolve) => { resolveFirst = resolve })
    const second = new Promise<PageResult<Demo>>((resolve) => { resolveSecond = resolve })
    const fetcher = vi.fn()
      .mockReturnValueOnce(first)
      .mockReturnValueOnce(second)
    const wrapper = mountHarness(fetcher, { immediate: false })

    wrapper.vm.loadData()
    wrapper.vm.loadData()
    // 新请求先返回
    resolveSecond(page([{ id: 2, name: 'b' }], 2, 2, 10))
    await flushPromises()
    expect(wrapper.vm.records).toEqual([{ id: 2, name: 'b' }])
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    // 旧请求后返回，不得覆盖新结果
    resolveFirst(page([{ id: 1, name: 'a' }], 1, 1, 10))
    await flushPromises()
    expect(wrapper.vm.records).toEqual([{ id: 2, name: 'b' }])
    expect(wrapper.vm.total).toBe(2)
  })

  it('过期请求的失败不污染最新请求状态', async () => {
    let resolveFirst!: (v: PageResult<Demo>) => void
    const first = new Promise<PageResult<Demo>>((resolve) => { resolveFirst = resolve })
    const fetcher = vi.fn()
      .mockReturnValueOnce(first)
      .mockReturnValueOnce(Promise.resolve(page([{ id: 3, name: 'c' }], 3, 1, 10)))
    const wrapper = mountHarness(fetcher, { immediate: false })

    wrapper.vm.loadData()
    wrapper.vm.loadData()
    await flushPromises()
    expect(wrapper.vm.records).toEqual([{ id: 3, name: 'c' }])
    expect(wrapper.vm.error).toBeNull()
    // 旧请求此刻才失败，不能把已成功的最新状态改成错误
    resolveFirst(page([], 0, 1, 10))
    await flushPromises()
    expect(wrapper.vm.records).toEqual([{ id: 3, name: 'c' }])
    expect(wrapper.vm.error).toBeNull()
  })

  it('新请求开始时 abort 前一个在途请求', async () => {
    const signals: Array<AbortSignal | undefined> = []
    const first = new Promise<PageResult<Demo>>((resolve) => { resolve(page([], 0, 1, 10)) })
    const fetcher = vi.fn()
      .mockImplementationOnce((_params: unknown, signal?: AbortSignal) => { signals.push(signal); return first })
      .mockImplementationOnce((_params: unknown, signal?: AbortSignal) => {
        signals.push(signal)
        return Promise.resolve(page([{ id: 9, name: 'z' }], 1, 1, 10))
      })
    const wrapper = mountHarness(fetcher, { immediate: false })

    wrapper.vm.loadData()
    wrapper.vm.loadData()
    await flushPromises()

    expect(signals[0]?.aborted).toBe(true)
    expect(signals[1]?.aborted).toBe(false)
    expect(wrapper.vm.records).toEqual([{ id: 9, name: 'z' }])
  })

  it('组件卸载时 abort 在途请求', async () => {
    const signals: Array<AbortSignal | undefined> = []
    const pending = new Promise<PageResult<Demo>>((resolve) => { resolve(page([], 0, 1, 10)) })
    const fetcher = vi.fn()
      .mockImplementationOnce((_params: unknown, signal?: AbortSignal) => { signals.push(signal); return pending })
    const wrapper = mountHarness(fetcher, { immediate: false })

    wrapper.vm.loadData()
    wrapper.unmount()

    expect(signals[0]?.aborted).toBe(true)
  })

  it('主动取消（ERR_CANCELED）不污染 error 状态', async () => {
    const cancelError = Object.assign(new Error('canceled'), { name: 'CanceledError', code: 'ERR_CANCELED' })
    const fetcher = vi.fn().mockRejectedValue(cancelError)
    const wrapper = mountHarness(fetcher, { immediate: false })

    wrapper.vm.loadData()
    await flushPromises()

    expect(wrapper.vm.error).toBeNull()
    expect(wrapper.vm.records).toEqual([])
  })
})
