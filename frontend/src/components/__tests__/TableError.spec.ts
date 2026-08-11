import { describe, expect, it } from 'vitest'
import { mountWithPlugins, findButtonByText } from '@/test/testUtils'
import TableError from '@/components/TableError.vue'

describe('TableError', () => {
  it('展示失败标题与错误信息', () => {
    const wrapper = mountWithPlugins(TableError, {
      props: { error: new Error('数据服务超时') },
    })
    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('数据服务超时')
  })

  it('无错误时不渲染 subtitle', () => {
    const wrapper = mountWithPlugins(TableError)
    expect(wrapper.find('.ant-result-subtitle').exists()).toBe(false)
  })

  it('点击重试触发 retry 事件', async () => {
    const wrapper = mountWithPlugins(TableError, {
      props: { error: new Error('boom') },
    })
    const retryBtn = findButtonByText(wrapper, '重试')
    expect(retryBtn).toBeTruthy()
    await retryBtn!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
