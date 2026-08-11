import { describe, expect, it } from 'vitest'
import { mountWithPlugins, findButtonByText } from '@/test/testUtils'
import ProSearchForm from '@/components/ProSearchForm.vue'
import type { SearchField } from '@/types'

const fields: SearchField[] = [
  { label: '状态', prop: 'status', type: 'select', options: [{ label: '启用', value: 1 }] },
  { label: '关键词', prop: 'keyword', placeholder: '输入名称' },
]

function mountForm() {
  return mountWithPlugins(ProSearchForm, { props: { fields } })
}

describe('ProSearchForm', () => {
  it('根据字段配置渲染输入框与下拉', () => {
    const wrapper = mountForm()
    expect(wrapper.find('.ant-select').exists()).toBe(true)
    expect(wrapper.find('input.ant-input').exists()).toBe(true)
  })

  it('输入关键词后查询携带搜索模型', async () => {
    const wrapper = mountForm()
    const input = wrapper.find('input.ant-input')
    await input.setValue('admin')
    await findButtonByText(wrapper, '查询')!.trigger('click')
    expect(wrapper.emitted('search')?.[0][0]).toMatchObject({ keyword: 'admin' })
  })

  it('重置清空条件并触发 reset', async () => {
    const wrapper = mountForm()
    const input = wrapper.find('input.ant-input')
    await input.setValue('admin')
    await findButtonByText(wrapper, '重置')!.trigger('click')
    expect(wrapper.emitted('reset')).toHaveLength(1)
    expect((input.element as HTMLInputElement).value).toBe('')
  })

  it('loading 时查询按钮禁用', () => {
    const wrapper = mountWithPlugins(ProSearchForm, { props: { fields, loading: true } })
    expect(findButtonByText(wrapper, '查询')!.classes()).toContain('ant-btn-loading')
  })
})
