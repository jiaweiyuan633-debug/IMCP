import type { Meta, StoryObj } from '@storybook/vue3'
import TableEmpty from './TableEmpty.vue'

/** 表格空态占位：默认「暂无数据」，可自定义描述与操作插槽。 */
const meta = {
  title: '组件/TableEmpty 空态',
  component: TableEmpty,
  argTypes: {
    description: { control: 'text', description: '空态描述文案' },
  },
  args: { description: '' },
  render: (args) => ({
    components: { TableEmpty },
    setup: () => ({ args }),
    template: '<TableEmpty :description="args.description" />',
  }),
} satisfies Meta<typeof TableEmpty>

export default meta
type Story = StoryObj<typeof meta>

export const 默认: Story = {}

export const 自定义描述: Story = {
  args: { description: '当前筛选条件下没有符合条件的记录，请调整查询条件' },
}

export const 带操作按钮: Story = {
  render: () => ({
    components: { TableEmpty },
    template: `
      <TableEmpty description="还没有任何成员">
        <template #action>
          <a-button type="primary">邀请成员</a-button>
        </template>
      </TableEmpty>
    `,
  }),
}
