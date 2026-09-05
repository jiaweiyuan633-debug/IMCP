# 多阶段构建：pnpm 构建 + 非 root nginx 托管（营销官网，静态站）
# 项目为 pnpm 工作区（pnpm-lock.yaml + pnpm-workspace.yaml），与 frontend 保持一致。
# 不能用 npm 安装：vite@^7 与 vite-plugin-pwa@0.21.x 的 peer 声明（仅到 vite6）会被 npm
# 严格校验拒绝（ERESOLVE）；pnpm 依据锁文件冻结安装可正常解析该树。
FROM node:26-alpine AS build
WORKDIR /app
# corepack 固定 pnpm 版本（与 package.json packageManager / CI pnpm 11.16.0 对齐）
RUN corepack enable && corepack prepare pnpm@11.16.0 --activate
# 官网依赖极少，pnpm 冻结锁文件安装
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

FROM nginxinc/nginx-unprivileged:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
