import { describe, expect, it } from 'vitest'
import { mountWithPlugins } from '@/test/testUtils'
import TableEmpty from '@/components/TableEmpty.vue'

describe('TableEmpty', () => {
  it('默认展示「暂无数据」', () => {
    const wrapper = mountWithPlugins(TableEmpty)
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('自定义描述文案生效', () => {
    const wrapper = mountWithPlugins(TableEmpty, {
      props: { description: '请先选择部门' },
    })
    expect(wrapper.text()).toContain('请先选择部门')
  })

  it('action 插槽渲染到 extra 区域', () => {
    const wrapper = mountWithPlugins(TableEmpty, {
      slots: { action: '<button class="custom-action">去新增</button>' },
    })
    expect(wrapper.find('button.custom-action').exists()).toBe(true)
  })
})
