import type { Meta, StoryObj } from '@storybook/vue3'
import GlobalSearch from './GlobalSearch.vue'
import { usePermissionStore } from '@/stores/permission'
import type { MenuNode } from '@/types'

/**
 * 全局菜单搜索：按 Ctrl+K（Mac ⌘K）唤起，实时过滤可见菜单，回车直达首个结果。
 *
 * 演示：故事渲染后请按 Ctrl+K 打开搜索框；或点击 play 按钮自动触发。
 */
const SAMPLE_MENUS: MenuNode[] = [
  {
    id: 1,
    parentId: 0,
    name: '系统管理',
    type: 'dir',
    icon: 'SettingOutlined',
    sort: 1,
    visible: 1,
    status: 1,
    children: [
      { id: 11, parentId: 1, name: '用户管理', type: 'menu', path: '/system/user', sort: 1, visible: 1, status: 1 },
      { id: 12, parentId: 1, name: '角色管理', type: 'menu', path: '/system/role', sort: 2, visible: 1, status: 1 },
      { id: 13, parentId: 1, name: '菜单管理', type: 'menu', path: '/system/menu', sort: 3, visible: 1, status: 1 },
    ],
  },
  {
    id: 2,
    parentId: 0,
    name: '设备管理',
    type: 'menu',
    path: '/device',
    sort: 2,
    visible: 1,
    status: 1,
  },
]

const meta = {
  title: '组件/GlobalSearch 全局搜索',
  component: GlobalSearch,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: '按下 Ctrl+K 唤起搜索框，支持模糊匹配菜单名与路由路径。',
      },
    },
  },
  render: () => ({
    components: { GlobalSearch },
    setup() {
      const permissionStore = usePermissionStore()
      permissionStore.menus = SAMPLE_MENUS
      return {}
    },
    template: `
      <div style="padding: 24px; background: #fff; border-radius: 8px; text-align: center">
        <p style="color: #8c8c8c; margin-bottom: 16px">按 <kbd>Ctrl</kbd> + <kbd>K</kbd> 打开全局搜索</p>
        <GlobalSearch />
      </div>
    `,
  }),
} satisfies Meta<typeof GlobalSearch>

export default meta
type Story = StoryObj<typeof meta>

/** 自动触发 Ctrl+K 打开搜索框，展示菜单过滤效果。 */
export const 搜索菜单: Story = {
  play: async ({ step }) => {
    await step('唤起全局搜索', () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))
    })
  },
}
