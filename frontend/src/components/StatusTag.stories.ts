import type { Meta, StoryObj } from '@storybook/vue3'
import StatusTag from './StatusTag.vue'

/**
 * 状态标签：把业务状态值（数字 0/1 或枚举字符串）映射为语义化颜色与国际化文案。
 *
 * 颜色映射：1 / SUCCEEDED / success → 绿；0 / FAILED / error → 红；
 * PENDING / QUEUED / RUNNING / processing → 蓝（processing）；CANCELLED / warning → 橙；其余灰。
 */
const meta = {
  title: '组件/StatusTag 状态标签',
  component: StatusTag,
  argTypes: {
    value: {
      control: { type: 'select' },
      options: ['1', '0', 'PENDING', 'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'APPROVED', 'REJECTED', 'UNKNOWN'],
      description: '业务状态值',
    },
  },
  args: { value: '1' },
} satisfies Meta<typeof StatusTag>

export default meta
type Story = StoryObj<typeof meta>

export const 启用: Story = { args: { value: '1' } }

export const 禁用: Story = { args: { value: '0' } }

export const 执行中: Story = { args: { value: 'RUNNING' } }

export const 成功: Story = { args: { value: 'SUCCEEDED' } }

export const 失败: Story = { args: { value: 'FAILED' } }

export const 已取消: Story = { args: { value: 'CANCELLED' } }

export const 未知值: Story = { args: { value: 'UNKNOWN' } }
