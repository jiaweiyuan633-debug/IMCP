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

function mountHarness(fetcher: (params: unknown) => Promise<PageResult<Demo>>, options?: unknown) {
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
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: 'admin', pageNum: 1, pageSize: 10 })
  })

  it('buildParams 映射查询参数', async () => {
    const fetcher = vi.fn().mockResolvedValue(page([], 0, 1, 10))
    const wrapper = mountHarness(fetcher, {
      buildParams: (query: Record<string, unknown>) => ({ keyword: (query.k as string) || '' }),
    })
    await flushPromises()
    wrapper.vm.onSearch({ k: '设备' })
    await flushPromises()
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '设备', pageNum: 1, pageSize: 10 })
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
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '', pageNum: 1, pageSize: 10 })
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
})
