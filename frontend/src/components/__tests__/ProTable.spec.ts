import { describe, expect, it } from 'vitest'
import { mountWithPlugins, findButtonByText } from '@/test/testUtils'
import ProTable from '@/components/ProTable.vue'

const baseProps = {
  columns: [
    { title: '用户', dataIndex: 'username', key: 'username' },
    { title: '状态', dataIndex: 'status', key: 'status' },
  ],
  dataSource: [
    { id: 1, username: 'admin', status: '启用' },
    { id: 2, username: 'alice', status: '禁用' },
  ],
  total: 2,
  pageNum: 1,
  pageSize: 10,
}

describe('ProTable', () => {
  it('渲染数据行', () => {
    const wrapper = mountWithPlugins(ProTable, { props: baseProps })
    const cells = wrapper.findAll('td')
    expect(cells.some((c) => c.text() === 'admin')).toBe(true)
    expect(cells.some((c) => c.text() === 'alice')).toBe(true)
  })

  it('分页变化回传 pageNum / pageSize 并触发 change', async () => {
    const wrapper = mountWithPlugins(ProTable, { props: { ...baseProps, total: 30 } })
    const table = wrapper.findComponent({ name: 'ATable' })
    expect(table.exists()).toBe(true)
    await table.vm.$emit('change', { current: 2, pageSize: 20 })
    expect(wrapper.emitted('update:pageNum')?.[0]).toEqual([2])
    expect(wrapper.emitted('update:pageSize')?.[0]).toEqual([20])
    expect(wrapper.emitted('change')).toHaveLength(1)
  })

  it('加载中透传 loading', () => {
    const wrapper = mountWithPlugins(ProTable, { props: { ...baseProps, loading: true } })
    const table = wrapper.findComponent({ name: 'ATable' })
    expect(table.props('loading')).toBe(true)
  })

  it('错误态渲染 TableError，点击重试触发 retry', async () => {
    // 错误态下数据源应为空，才能命中表格的 empty 插槽
    const wrapper = mountWithPlugins(ProTable, {
      props: { ...baseProps, dataSource: [], total: 0, error: new Error('网络异常') },
    })
    const retryBtn = findButtonByText(wrapper, '重试')
    expect(retryBtn).toBeTruthy()
    await retryBtn!.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
