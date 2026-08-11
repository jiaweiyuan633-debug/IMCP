import { describe, expect, it } from 'vitest'
import { mountWithPlugins } from '@/test/testUtils'
import StatusTag from '@/components/StatusTag.vue'

/** 状态映射核心行为：数字/枚举值 → 国际化文案 + 语义化颜色。 */
describe('StatusTag', () => {
  function tag(value: number | string) {
    return mountWithPlugins(StatusTag, { props: { value } })
  }

  it('1 映射为「启用」并使用 success 色', () => {
    const wrapper = tag('1')
    expect(wrapper.text()).toBe('启用')
    expect(wrapper.find('span').classes()).toContain('ant-tag-success')
  })

  it('0 映射为「禁用」并使用 error 色', () => {
    const wrapper = tag('0')
    expect(wrapper.text()).toBe('禁用')
    expect(wrapper.find('span').classes()).toContain('ant-tag-error')
  })

  it('SUCCEEDED 映射为「成功」', () => {
    expect(tag('SUCCEEDED').text()).toBe('成功')
  })

  it('FAILED 映射为「失败」', () => {
    expect(tag('FAILED').text()).toBe('失败')
  })

  it('RUNNING 映射为「执行中」并使用 processing 色', () => {
    const wrapper = tag('RUNNING')
    expect(wrapper.text()).toBe('执行中')
    expect(wrapper.find('span').classes()).toContain('ant-tag-processing')
  })

  it('CANCELLED 映射为「已取消」并使用 warning 色', () => {
    const wrapper = tag('CANCELLED')
    expect(wrapper.text()).toBe('已取消')
    expect(wrapper.find('span').classes()).toContain('ant-tag-warning')
  })

  it('未知值原样展示并使用 default 色', () => {
    const wrapper = tag('CUSTOM_STATE')
    expect(wrapper.text()).toBe('CUSTOM_STATE')
    expect(wrapper.find('span').classes()).toContain('ant-tag-default')
  })
})
