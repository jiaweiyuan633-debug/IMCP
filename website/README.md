# 官网（website/）

面向外部客户与访客的**营销官网**：产品展示、品牌认知与需求（预约演示/线索）转化。与管理端后台（`frontend/`）是两个独立工程，内容以静态站方式交付，默认运行在 `http://localhost:5174`。

## 技术栈

- Vue 3 + Vite 7（`vite.config.ts`，server port 5174），图标 `lucide-vue-next`，PWA 支持（`vite-plugin-pwa`，自动更新）。
- 单页结构：全部内容在 `src/App.vue`（Hero / 产品能力 / 平台体验 / 解决方案 / 定价 / 预约演示表单 / 页脚），样式集中在 `src/styles.css`，视觉素材 `src/assets/dashboard-preview.svg`。
- **无独立 tsconfig / vue-tsc / lint / 测试脚本**：`pnpm build` 只执行 `vite build`（Vite 用 esbuild 转译 TS，不做类型检查）。CI（`.github/workflows/ci.yml`）对 website 的门禁仅为 `pnpm build`。
- 依赖与脚本以 `package.json` 为准：`dev` / `build` / `preview`。

## 本地运行与构建

```bash
cd website
pnpm install
pnpm dev           # http://localhost:5174

pnpm build         # 产物输出 website/dist/
pnpm preview       # 本地预览构建产物
```

构建产物为纯静态文件，可由任意静态服务器托管；容器化交付见 `Dockerfile`（多阶段构建 + `nginx.conf` 非 root nginx 托管）。

## 页面区块（App.vue）

| 锚点 | 内容 |
| --- | --- |
| `#top`（hero） | 主视觉与核心卖点、试用/查看平台 CTA |
| `#features` | 产品能力：组织权限、流程引擎、AI 编排、实时监控告警、多租户、开放工程基线 |
| `#product` | 平台体验：后台界面与关键体验点 |
| `#solutions` | 解决方案：中小企业 / 集团多组织 / 服务商交付 |
| `#pricing` | 定价：标准版、专业版、旗舰版 |
| `#contact` | 预约演示表单 + 联系方式 |

## 配置驱动说明（env 注入，未配置即演示模式）

代码里**没有写死的联系方式/表单端点/埋点 ID**，全部由 Vite 构建期环境变量（`import.meta.env`）驱动，声明见 `src/env.d.ts`，模板见 `website/.env.production`（**当前提交的 `.env.production` 全部留空 = 演示站点行为**）。

| 变量 | 作用 | 留空时的行为（按当前代码） |
| --- | --- | --- |
| `VITE_LEAD_ENDPOINT` | 预约表单线索提交端点（POST JSON） | 表单禁用，提交按钮下方提示"演示模式：表单未启用"，绝不假装提交成功；表单内置 honeypot 与 30s 提交冷却 |
| `VITE_CONTACT_PHONE` / `VITE_CONTACT_EMAIL` | 官网对外联系方式 | 不渲染联系方式区块（`App.vue` 用 `contactChannels.length` 判空，展示"演示站点 — 联系方式待上线配置后开放"） |
| `VITE_COMPANY_NAME` | 页脚/版权企业名 | 回退产品名"智能管理平台" |
| `VITE_GA_ID` / `VITE_GTM_ID` | GA4 / GTM 埋点 | `analytics.ts` 保持 no-op：不加载 googletagmanager 脚本、不伪造上报（开发模式仅 console.debug 提示一次） |

使用方式：

```bash
# 方式一：直接编辑 .env.production 填入正式值后重新构建
# 方式二：构建时注入同名变量（VITE_* 是构建期变量，运行时容器环境变量不会生效，
#         若走 Docker/CI 需在 Dockerfile 或流水线里透传 build-arg / env 再重新 build）
```

> 注：`.env.production` 顶部注释提示"启用 GA/GTM 埋点后需同步放宽 nginx 的 CSP（script-src/connect-src 放行 googletagmanager/google-analytics）"——当前 `website/nginx.conf` 并未配置 CSP；若你的部署网关或后续新增的 nginx 配置启用了严格 CSP，请按该提示放行对应域名。

## 上线前检查清单（正式发布前逐项确认）

1. 联系方式与表单端点：`VITE_CONTACT_PHONE` / `VITE_CONTACT_EMAIL` / `VITE_LEAD_ENDPOINT` 填入**真实**值（当前为演示占位，`.env.production` 全空）；端点需支持 JSON POST 并做好幂等/反爬（前端仅有限频与 honeypot）。
2. 埋点：确认 `VITE_GA_ID` 或 `VITE_GTM_ID`，并在有严格 CSP 的网关放行对应域名。
3. 域名/品牌：`VITE_COMPANY_NAME`、`index.html` 的 title/description 与实际部署域名一致。
4. 重新 `pnpm build` 并验证：`pnpm preview` 后检查表单可提交、联系方式正常渲染、无第三方脚本误加载。
5. 部署形态核对：静态产物由 nginx（`website/nginx.conf`）/CDN 托管，PWA 自动更新；`index.html` 与 `sw.js` 不缓存（nginx 已配）。

> 文案/素材均位于 `src/App.vue` 与 `src/styles.css`、`src/assets/`，改版直接改源码，无内容管理系统。
