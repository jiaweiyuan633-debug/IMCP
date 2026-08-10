# 多阶段构建：vite 构建 + 非 root nginx 托管（营销官网，静态站）
FROM node:22-alpine AS build
WORKDIR /app
# 官网依赖极少且无锁文件，使用 npm 安装
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
