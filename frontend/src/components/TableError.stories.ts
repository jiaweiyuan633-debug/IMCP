import type { Meta, StoryObj } from '@storybook/vue3'
import TableError from './TableError.vue'

/** 表格加载失败态：展示错误信息并提供「重试」按钮，点击触发 retry 事件。 */
const meta = {
  title: '组件/TableError 错误态',
  component: TableError,
  argTypes: {
    error: { control: 'object', description: '错误对象，message 将展示在错误卡片中' },
  },
  args: { error: null },
  render: (args) => ({
    components: { TableError },
    setup: () => ({ args }),
    template: '<TableError :error="args.error" />',
  }),
} satisfies Meta<typeof TableError>

export default meta
type Story = StoryObj<typeof meta>

export const 默认: Story = {}

export const 带错误信息: Story = {
  args: { error: new Error('网络异常，请稍后重试') },
}

export const 服务端错误码: Story = {
  args: { error: new Error('请求失败（500）：数据服务暂不可用') },
}
