# 管理端前端（frontend/）

Vue 3 + TypeScript + Vite 的后台管理系统，组件库为 Ant Design Vue 4。与 `website/`（对外营销官网）分离，目录内即为完整的前端工程。

版本与脚本以 `package.json` 为准（Vite 7 / Vue 3.5 / ant-design-vue 4 / Pinia / vue-router 4 / vue-i18n 9 / @tanstack/vue-query 5 / axios / dayjs / echarts）。

## 快速开始

```bash
pnpm install
pnpm dev          # http://localhost:5173
pnpm lint         # ESLint（CI 门禁，要求 0 error）
pnpm test         # Vitest 全量（CI 门禁，含覆盖率门槛）
pnpm build        # vue-tsc --noEmit 类型检查 + vite build（CI 门禁）
```

- 本地联调默认直连后端：`frontend/.env.development` 中 `VITE_API_BASE_URL=http://localhost:8080/api`（需先启动后端，见仓库根 README）；生产构建默认同源 `/api`，由 Ingress/网关反代（见 `frontend/Dockerfile` 的 `ARG VITE_API_BASE_URL=/api`）。
- 更多脚本：`pnpm preview`（本地预览构建产物）、`pnpm test:coverage`（输出覆盖率）、`pnpm storybook` / `pnpm build-storybook`（组件开发）。

## 目录约定

```
src/
├── api/           # 接口层：每模块一个文件（auth/system/device/ai/…），显式 Vo 类型契约
│   └── __tests__/ # API 契约测试（锁定方法/URL/载荷形状）
├── components/    # 沉淀组件：ProTable / ProSearchForm / ModalForm / FileUpload / StatusTag /
│                  #   TableEmpty / TableError / GlobalSearch（配 __tests__ 与 *.stories.ts）
├── composables/   # 组合式函数：useTableQuery（列表页统一状态）/ useSystemTitle（标题）
├── directives/    # v-permission 按钮级权限指令（仅做展示层过滤，安全边界在后端）
├── layout/        # BasicLayout：侧栏/顶栏/标签页/keep-alive、通知 SSE 与消息 WS
├── locales/       # zh-CN.ts / en-US.ts 语言包（必须成对维护）
├── router/        # index.ts（守卫 + 动态路由装载）/ dynamic.ts（菜单 → 路由）
├── stores/        # Pinia：user / permission / app（跨页共享状态）
├── styles/        # global.css
├── test/          # testUtils.ts（测试工具）
├── types/         # 全局类型（Result / PageResult / UserInfo / MenuNode / LoginResponse 等）
├── utils/         # request.ts（axios 封装）/ env.ts / auth.ts / validation.ts / download.ts /
│                  #   date.ts / table.ts / fileUrl.ts / menuPath.ts / bizRoute.ts
└── views/         # 页面：views/{module}/{page}/index.vue
```

## 请求层与类型契约（src/utils/request.ts）

- **BaseURL**：`API_BASE_URL = VITE_API_BASE_URL || '/api'`（去尾部 `/`），见 `utils/env.ts`。
- **统一信封**：后端返回 `Result<T> = { code, message, data }`。响应拦截器在 `code === 0` 时把 `data` 解包返回；业务错误（`code !== 0`）弹出按错误码本地化的提示（`error.{code}`，无匹配回退 `message`）并以带 `code` 的 `Error` reject。
- **泛型契约**：`request.get<T>(...)` / `post<T>` / `put<T>` / `delete<T>`。api 层函数必须显式声明返回类型并优先传 `<T>`，例如：

  ```ts
  export function getDevicePage(params: Record<string, unknown>, signal?: AbortSignal): Promise<PageResult<DeviceVo>> {
    return request.get<PageResult<DeviceVo>>('/device', { params, signal })
  }
  ```

  `any` / `@ts-ignore` 属违规（vue-tsc 门禁兜底）。
- **401 刷新**：refresh token 存后端 httpOnly Cookie，axios 实例 `withCredentials: true`。首个 401 触发共享刷新（`POST /auth/refresh`），并发 401 只刷新一次；刷新失败清 token 并跳登录。
- **网络重试**：仅幂等方法（GET/HEAD/OPTIONS）在 `ECONNABORTED/ERR_NETWORK` 时延迟 500ms 重试一次；写操作不自动重试，由调用方决定（避免重复提交）。
- **主动取消**：`AbortController` 取消（axios `ERR_CANCELED`）静默透传，不弹错、不重试、不回写状态。
- **文件下载/导出**：`responseType: 'blob'` 的响应跳过信封解析直接返回完整 response；文件名统一经 `utils/download.ts` 的 `parseContentDispositionFilename` + `triggerBlobDownload` 处理（参考 `api/system.ts` 的 `exportUsers`）。文件访问 URL 一律走 `utils/fileUrl.ts` 的 `withFileToken`/`absoluteFileUrl` 现取令牌拼接，禁止各页手写。

## 状态管理边界

- **列表页 → `useTableQuery`（src/composables/useTableQuery.ts）**：统一管理 `pageNum/pageSize/total/loading/records/searchModel/error`，内置**请求序号守卫 + AbortController**（新请求 abort 旧请求、过期响应不写回、卸载时中断在途请求）。默认 `onMounted` 自动加载；多列表页用 `immediate: false` 自行控制。传入的 fetcher 建议签名 `(params, signal?) => Promise<PageResult<T>>`（api 函数第二个参数透传 `signal` 才能真正中断网络）。

  ```ts
  const { pageNum, pageSize, total, loading, records, error, loadData, onSearch, onReset } =
    useTableQuery<DeviceVo>(getDevicePage, {
      buildParams: (q) => ({ deviceCode: q.deviceCode as string | undefined, ... }),
    })
  ```

- **跨页缓存数据 → TanStack Query**：`@tanstack/vue-query` 已注册（`src/main.ts` + `src/queryClient.ts`，默认 `retry: 1, staleTime: 30s`）。适合看板统计等需要缓存/后台语义的只读数据（如 `views/dashboard` 的 `useQuery`）。**不要在列表页用 useQuery 重造分页/搜索/手动刷新状态**——那是 `useTableQuery` 的职责。
- **Pinia → 仅放跨页共享会话/UI 状态**：`user`（token/用户信息/强制改密标记）、`permission`（menus/perms/routesLoaded）、`app`（折叠/主题/语言/标签页）。不缓存服务端列表数据。

## 路由、菜单与权限接入

- **静态路由**：`/login`、`/oauth/callback`、`/change-password`（强制改密页，`meta.public`）；`/` 挂 `BasicLayout`（无静态子路由）；`/:pathMatch(.*)*` 为兜底 NotFound。
- **动态路由（后端菜单驱动）**：守卫在未装载时调 `GET /auth/me` 拿 `menus`，`router/dynamic.ts` 的 `buildDynamicRouteChildren` 过滤 `button` 类型与 `status/visible` 后，用 `import.meta.glob('../views/**/*.vue')` 把菜单 `component` 映射为页面并 `router.addRoute('Root', route)`，路由名为 `Menu-{id}`（稳定，作为 keep-alive 缓存键）。新增页面**不需要手写路由**，只用在后端菜单表登记（含 `component` 路径与 `perm` 编码）。
- **权限**：按钮/操作级用 `v-permission="'system:user:add'"` 指令按 `permissionStore.perms` 过滤；权限码来自后端 `user.perms`。**这只是 UX**：接口安全由后端 `@PreAuthorize` 兜底，前端过滤可被绕过。
- **改密拦截**：`mustChangePassword` 为真时守卫强制跳转 `/change-password`，改密成功后清除标记。
- 菜单路径解析统一走 `utils/menuPath.ts`（`resolveMenuPath/fullPathOf`），禁止页面自算路径。

## 实时通道（SSE 与 WebSocket）

BasicLayout 中两条实时通道都用**短期一次性 ticket** 鉴权（先 `GET {API_BASE_URL}/system/notice/ticket` 取票，再拼到 URL），不在地址上暴露长期 token：

- 站内通知：SSE `EventSource`，地址 `${API_BASE_URL}/system/notice/stream?ticket=…`。
- 站内消息：WebSocket，地址由 `API_BASE_URL` 推导 `ws(s)://<origin>/ws/messages?ticket=…`。
- 两者均做指数退避重连（上限 5 次，封顶 30s），组件卸载后停止重连。

**代理配置联动**：新增/调整实时端点时按部署形态同步改三处之一——

| 形态 | 配置位置 |
| --- | --- |
| K8s | Chart Ingress（`k8s/helm/admin-scaffold/templates/all.yaml`）：`/api`、`/files`、`/uploads`、`/ws` 直达 backend Service |
| 本地 Docker 栈 | `docker/nginx.conf` 同路径集合反代到 backend |
| 生产前端 nginx | `frontend/nginx.conf` 是纯静态托管（`/api` 由 Ingress 转发，不在容器内二次代理） |

本地 `pnpm dev` 无代理（`.env.development` 直连后端），不涉及上述配置。

## 主题、i18n 与持久化

- **暗黑主题**：`stores/app.ts` 的 `darkTheme`，初始化/切换即写回 `localStorage['admin_dark_theme']`；`App.vue` 的 `a-config-provider` 按状态切换 antd `darkAlgorithm/defaultAlgorithm`（borderRadius 6）。
- **i18n**：`src/locales/index.ts`（locale 默认读 `localStorage['admin_locale']`，fallback `zh-CN`）。新增文案必须同步维护 `zh-CN.ts` 与 `en-US.ts` 两份——缺 key 时英文界面会回退中文。菜单/面包屑标题若本身是 i18n key（`te()` 命中）会随语言翻译。
- **PWA**：`vite-plugin-pwa` 自动更新，`main.ts` `registerSW({ immediate: true })`；`index.html`/`sw.js` 不缓存（nginx 已配 `Cache-Control: no-cache`），离线时 BasicLayout 顶部有提示条。
- **凭证跨标签页同步**：`utils/auth.ts` 监听 `storage` 事件（登出广播 `admin_auth_cleared_at`），一个标签页登出/清 token，其余标签页同步失效。

## 组件库（src/components）

| 组件 | 说明 |
| --- | --- |
| `ProTable` | 受控 `a-table` 封装：接收 `columns/dataSource/loading/total/pageNum/pageSize/rowKey/rowSelection/error`，对外发 `update:pageNum / update:pageSize / change / retry`；空态走 `#empty` 插槽，错误态自动渲染 `TableError` |
| `ProSearchForm` | 声明式搜索表单：`fields: SearchField[]`（`input/select`），触发 `search/reset` |
| `ModalForm` | 弹窗表单壳：`open/title/loading`，提交态 `confirm-loading`，发 `ok` / `update:open` |
| `FileUpload` | 文件上传（含头像），值回写 `fileUrl` |
| `StatusTag` | 状态标签 |
| `TableEmpty` / `TableError` | 表格空态与错误态（含重试按钮） |
| `GlobalSearch` | 全局菜单搜索 |

统一从 `@/components` 导入（含 `useTableQuery` 的再导出）。修改组件必须同步 `__tests__/*.spec.ts` 与 `*.stories.ts`。

## 测试与门禁

- CI（`.github/workflows/ci.yml`）对 frontend 执行：`pnpm lint` → `pnpm exec vitest run --coverage` → `pnpm build`，三者全绿才放行。
- 覆盖率门槛在 `vitest.config.ts`：lines 32 / functions 23 / branches 31 / statements 32（v8 provider）。新增核心逻辑必须带测试；组件/工具改动需更新对应用例与 stories。
- Storybook：`pnpm storybook`（:6006）用于组件开发与 a11y 检查；`build-storybook` 未接入 CI，需手工跑。

## 新增一个业务页面

起点用后端 CRUD 生成器（`scripts/crud-gen`，详见 [`../scripts/crud-gen/README.md`](../scripts/crud-gen/README.md)），它能同时产出后端 7 个 Java 文件与前端 `src/api/{module}.ts` + `src/views/{module}/{kebab}/index.vue` 骨架：

1. **表与菜单**：按 `docs/database/README.md` 补 Flyway 迁移建表；在后端菜单表登记目录/菜单/按钮（含 `component` 与 `perm` 编码）并授权，前端无需手写路由。
2. **跑生成器**：`python scripts/crud-gen/crud_gen.py <spec.json>`（规格见其 README），生成到对应 module 目录。
3. **API 层**：核对生成的 `src/api/{module}.ts`——保持「函数 + 显式 Vo/请求类型 + 泛型 request」形态；列表函数需可作 `useTableQuery` 的 fetcher（签名含 `signal`）。
4. **页面落地**：把生成的列表页骨架接 `ProTable + ProSearchForm + ModalForm + useTableQuery`（可直接参考 `src/views/device/list/index.vue` 的完整写法），操作按钮加 `v-permission`，`columns` 时间列用 `dateColumn`。
5. **i18n 化（合入红线）**：生成器的页面文案是中文直写，合入前必须全部改为 `zh-CN.ts` / `en-US.ts` 的 key（当前平台全量中英文国际化是交付约束，中文直写页会破坏 en-US 界面）。
6. **测试**：新增 api 契约测试（参考 `src/api/__tests__/system.spec.ts`）；页面级交互需冒烟时补充组件测试。
