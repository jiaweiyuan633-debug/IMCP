import type { Meta, StoryObj } from '@storybook/vue3'
import FileUpload from './FileUpload.vue'

/**
 * 图片上传：已传则显示缩略图头像，未传显示上传按钮。
 * 上传走统一文件服务（带鉴权 token），事件 update:value 回传文件 URL。
 */
const meta = {
  title: '组件/FileUpload 图片上传',
  component: FileUpload,
  argTypes: {
    value: { control: 'text', description: '已上传文件的 URL（相对路径或完整地址）' },
  },
  args: { value: '' },
  render: (args) => ({
    components: { FileUpload },
    setup: () => ({ args }),
    template: '<FileUpload :value="args.value" />',
  }),
} satisfies Meta<typeof FileUpload>

export default meta
type Story = StoryObj<typeof meta>

export const 未上传: Story = {}

export const 已上传: Story = {
  args: { value: 'https://picsum.photos/seed/admin/128/128' },
}
