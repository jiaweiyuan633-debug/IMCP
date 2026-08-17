# 管理端前端（frontend/）

Vue 3 + TypeScript + Vite 7 + Ant Design Vue 4 的后台管理系统。与 `website/`（官网）分离。

## 快速开始

```bash
pnpm install
pnpm dev          # http://localhost:5173（需后端运行，见根 README）
pnpm lint         # ESLint（CI 门禁，0 error）
pnpm test         # Vitest（CI 门禁 + 覆盖率阈值 32/23/31/32）
pnpm build        # vue-tsc 类型检查 + vite build（CI 门禁）
```

## 目录约定

```
src/
├── api/           # 接口层：每模块一个文件，显式 Vo 类型契约（泛型 request.get<T>）
│   └── __tests__/ # API 契约测试（锁定方法/URL/载荷形状）
├── components/    # 沉淀组件：ProTable / ProSearchForm / ModalForm / FileUpload /
│                  #   StatusTag / GlobalSearch（含 __tests__ 与 *.stories.ts）
├── composables/   # 可复用组合式函数（useTableQuery / useSystemTitle）
├── directives/    # v-permission 按钮级权限指令（仅 UX，安全边界在后端）
├── layout/        # BasicLayout（菜单/通知/消息实时推送 SSE+WS）
├── locales/       # zh-CN.ts / en-US.ts 语言包（**必须成对维护**）
├── router/        # 路由：index.ts 守卫 + dynamic.ts 动态路由（菜单驱动）
├── stores/        # Pinia：user / permission / app
├── types/         # 全局类型（Result / UserInfo / LoginResponse 等）
├── utils/         # request.ts（axios 封装）/ auth.ts / validation.ts 等
└── views/         # 页面：views/{module}/{page}/index.vue
```

## 新增一个业务模块（完整链路）

1. **后端**：Flyway 迁移建表 → CRUD 代码生成器（`scripts/crud-gen`）→ Controller/Service/Entity。
2. **API 层**：`src/api/{module}.ts` 定义接口与类型（参考 `src/api/device.ts`）：
   ```ts
   export function getDevicePage(params: DeviceQuery): Promise<PageResult<DeviceVo>> {
     return request.get<PageResult<DeviceVo>>('/device/device', { params })
   }
   ```
3. **页面**：`src/views/{module}/{page}/index.vue`，基于 `ProTable` + `ProSearchForm` + `ModalForm` 组合：
   ```vue
   <ProTable ref="tableRef" :columns="columns" :request="loadData" row-key="id">
     <template #toolbar> ... </template>
   </ProTable>
   ```
4. **菜单/权限**：后端菜单表新增（`perm` 驱动动态路由与按钮指令），前端无需手写路由。
5. **i18n**：页面文案全部走 `t('...')`，同步维护 `zh-CN.ts` 与 `en-US.ts`（新增 key 两份都要加，漏加时英文界面回退中文）。

## 关键约定

- **类型契约**：`request` 泛型化，api 层必须显式传 `<T>`；禁止 `any`/`@ts-ignore`（vue-tsc 门禁）。
- **请求层**（`utils/request.ts`）：统一 401 刷新（refresh token 走 httpOnly Cookie）、
  网络错误仅幂等方法自动重试、主动取消静默透传。文件导出/下载**必须复用 `service` 管道**
  并检查 `content-type`（避免 JSON 错误被当文件下载）。
- **实时通道**：SSE 与 WebSocket 都用**一次性 ticket** 鉴权（不暴露长期 token）；两者都有
  指数退避重连（上限 5 次）；新增实时通道必须同步三处代理配置（`frontend/nginx.conf` /
  `docker/nginx.conf` / K8s Ingress）。
- **权限只是 UX**：`v-permission` 与动态菜单只做展示层过滤，接口安全由后端 `@PreAuthorize` 兜底。
- **跨标签页凭证同步**：`utils/auth.ts` 的 storage 事件机制；新增登出/刷新路径保持该契约。
- **i18n 对齐纪律**：新增文案必须同步 `zh-CN.ts` 与 `en-US.ts` 两份（当前 1087 对 key 全对齐）。
- **keep-alive**：`BasicLayout` 全局 `max=10` 有界缓存；大列表页（如操作日志）谨慎开启，避免内存增长。

## 组件库（components/）

| 组件 | 用途 |
| --- | --- |
| `ProTable` | 列表页表格：request 拉数据、分页、loading、错误态、空态 |
| `ProSearchForm` | 搜索表单：fields 声明式生成查询条件 |
| `ModalForm` | 弹窗表单：open/loading/提交态封装 |
| `FileUpload` | 文件上传（含头像），返回 fileUrl |
| `StatusTag` | 状态标签（启用/禁用等） |
| `GlobalSearch` | 全局搜索 |

组件均有 `__tests__` 与 Storybook stories（`*.stories.ts`），修改组件必须同步更新测试。

## 测试

- `vitest`：组件测试 + 请求层契约测试 + store 测试。覆盖率门槛在 `vitest.config.ts`
  （lines 32 / functions 23 / branches 31 / statements 32），新增核心逻辑必须带测试。
- `storybook`：`pnpm storybook`（组件开发与 a11y 检查），CI 未接入（`build-storybook` 需手工跑）。
