# Docker

Compose 编排 MySQL、Redis、Java 后端、AI 服务、Nginx 前端五个服务。

启动：

```bash
docker compose up -d --build
```

环境变量示例见 `.env.example`，主机端口可通过 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT` 覆盖。

前端 Nginx 负责反向代理 `/api` 和 `/uploads`，AI 回调地址通过 `CALLBACK_BASE_URL` 指向容器内后端。

