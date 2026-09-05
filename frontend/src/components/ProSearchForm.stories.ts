import type { Meta, StoryObj } from '@storybook/vue3'
import ProSearchForm from './ProSearchForm.vue'
import type { SearchField } from '@/types'

/**
 * 列表页统一搜索条：根据字段配置渲染输入框/下拉，查询/重置/刷新一键联动。
 *
 * 事件：search（携带搜索模型）、reset（重置所有条件）。
 */
const defaultFields: SearchField[] = [
  { label: '状态', prop: 'status', type: 'select', options: [
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 },
  ] },
  { label: '关键词', prop: 'keyword', placeholder: '输入名称/编码' },
]

const meta = {
  title: '组件/ProSearchForm 搜索条',
  component: ProSearchForm,
  argTypes: {
    loading: { control: 'boolean', description: '查询按钮加载态' },
  },
  args: {
    loading: false,
    fields: defaultFields,
  },
  render: (args) => ({
    components: { ProSearchForm },
    setup: () => ({ args, onSearch: () => {}, onReset: () => {} }),
    template: '<ProSearchForm :fields="args.fields" :loading="args.loading" @search="onSearch" @reset="onReset" />',
  }),
} satisfies Meta<typeof ProSearchForm>

export default meta
type Story = StoryObj<typeof meta>

export const 默认: Story = {}

export const 加载中: Story = { args: { loading: true } }

export const 单条件搜索: Story = {
  args: {
    fields: [
      { label: '所属部门', prop: 'deptId', type: 'select', options: [
        { label: '技术部', value: 1 },
        { label: '产品部', value: 2 },
      ] },
    ],
  },
}
