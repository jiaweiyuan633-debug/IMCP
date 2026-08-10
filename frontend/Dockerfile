# 多阶段构建：pnpm 构建 + 非 root nginx 托管
# nginx-unprivileged 以 UID 101 运行（与 k8s securityContext.runAsUser 对齐），监听 8080
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# VITE_API_BASE_URL 由 CI 注入（docker build --build-arg）；默认 /api 走同源反代，
# 未注入时构建仍可通过
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN pnpm build

FROM nginxinc/nginx-unprivileged:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
