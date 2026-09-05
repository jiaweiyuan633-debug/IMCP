import type { Meta, StoryObj } from '@storybook/vue3'
import ModalForm from './ModalForm.vue'

/**
 * 弹窗表单容器：统一确定/取消按钮文案，加载中禁用确认，取消自动同步关闭状态。
 *
 * 事件：ok（点击确认）、update:open（点击取消/遮罩时置 false）。
 */
const meta = {
  title: '组件/ModalForm 弹窗表单',
  component: ModalForm,
  argTypes: {
    open: { control: 'boolean', description: '是否显示' },
    title: { control: 'text', description: '标题' },
    width: { control: 'number', description: '宽度（px）' },
    loading: { control: 'boolean', description: '提交中，确认按钮转圈并禁用' },
  },
  args: { open: true, title: '新增用户', width: 520, loading: false },
  render: (args) => ({
    components: { ModalForm },
    setup: () => ({ args }),
    template: `
      <ModalForm :open="args.open" :title="args.title" :width="args.width" :loading="args.loading">
        <a-form layout="vertical">
          <a-form-item label="用户名"><a-input placeholder="请输入用户名" /></a-form-item>
          <a-form-item label="角色"><a-select placeholder="请选择角色" style="width: 100%" /></a-form-item>
        </a-form>
      </ModalForm>
    `,
  }),
} satisfies Meta<typeof ModalForm>

export default meta
type Story = StoryObj<typeof meta>

export const 默认: Story = {}

export const 提交中: Story = { args: { loading: true } }

export const 宽屏: Story = { args: { width: 900 } }

export const 关闭状态: Story = { args: { open: false } }
