import type { Meta, StoryObj } from '@storybook/vue3'
import ProTable from './ProTable.vue'

/**
 * 列表页统一表格：分页（页码/页大小受控）+ 空态 + 错误态 + bodyCell 插槽。
 *
 * 事件：update:pageNum、update:pageSize、change（分页变化）、retry（错误重试）。
 */
const meta = {
  title: '组件/ProTable 数据表格',
  component: ProTable,
  argTypes: {
    loading: { control: 'boolean' },
    total: { control: 'number' },
    pageNum: { control: 'number' },
    pageSize: { control: 'number' },
    error: { control: 'object' },
  },
  args: {
    columns: [
      { title: '用户', dataIndex: 'username', key: 'username' },
      { title: '状态', dataIndex: 'status', key: 'status' },
    ],
    dataSource: [
      { id: 1, username: 'admin', status: '启用' },
      { id: 2, username: 'alice', status: '启用' },
      { id: 3, username: 'bob', status: '禁用' },
    ],
    total: 3,
    pageNum: 1,
    pageSize: 10,
    loading: false,
    error: null,
  },
  render: (args) => ({
    components: { ProTable },
    setup: () => ({ args }),
    template: `
      <ProTable
        :columns="args.columns"
        :data-source="args.dataSource"
        :loading="args.loading"
        :total="args.total"
        :page-num="args.pageNum"
        :page-size="args.pageSize"
        :error="args.error"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === '启用' ? 'green' : 'red'">{{ record.status }}</a-tag>
          </template>
        </template>
      </ProTable>
    `,
  }),
} satisfies Meta<typeof ProTable>

export default meta
type Story = StoryObj<typeof meta>

export const 默认: Story = {}

export const 加载中: Story = { args: { loading: true, dataSource: [] } }

export const 多页数据: Story = { args: { total: 58, pageNum: 3 } }

export const 错误重试: Story = {
  args: { error: new Error('加载失败，请重试') },
}

export const 空数据: Story = { args: { dataSource: [], total: 0 } }
