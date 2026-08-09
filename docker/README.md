# Docker

Compose 编排六个服务：

数据库结构由后端 Flyway V1-V29 自动迁移，禁止手工改库。

| 服务 | 说明 |
| --- | --- |
| `mysql` | MySQL 8，带健康检查与数据卷 |
| `redis` | Redis 7，开启 AOF 持久化，带健康检查 |
| `backend` | Java 后端，依赖 MySQL/Redis 健康检查 |
| `ai-service` | FastAPI AI 服务，依赖 Redis |
| `frontend` | Nginx + Vue3 构建产物，反向代理 `/api` 与 `/uploads` |
| `website` | Nginx + Y15智能管理平台官网，默认端口 `8081` |

启动：

```bash
cd docker
docker compose up -d --build
```

环境变量示例见 [docker/.env.example](.env.example)，主机端口可通过 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT`、`WEBSITE_PORT` 覆盖。

关键配置：

- 后端通过 `CALLBACK_BASE_URL=http://backend:8080` 接收 AI 回调
- AI 服务通过 `REDIS_URL` 连接 Redis，并通过 `CALLBACK_TOKEN` 与 Java 后端握手
- 文件存储默认使用后端容器内本地目录；如需 MinIO，设置 `STORAGE_TYPE=minio` 和 `MINIO_*` 环境变量，并确保后端容器能访问 MinIO 地址

Nginx 对 `location /api/` 和 `/uploads/` 做反向代理，前端路由回退到 `index.html`，支持刷新页面不 404。
官网独立使用 `nginx-website.conf`，通过 `WEBSITE_PORT` 暴露。

工程与设计规约见 `docs/architecture-conventions.md`，阿里规约合规说明见 `docs/alibaba-compliance.md`。

