# 部署教程

## 1. 本地开发

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

```bash
cd backend && mvn spring-boot:run
cd ai-service && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev
```

后端通过环境变量连接基础设施：

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=admin_scaffold
DB_USERNAME=root
DB_PASSWORD=
REDIS_HOST=localhost
REDIS_PORT=6379
CALLBACK_BASE_URL=http://127.0.0.1:8080
AI_BASE_URL=http://127.0.0.1:8000
SQL_LOG_THRESHOLD_MS=50
```

## 2. Docker Compose

```bash
cd docker
docker compose up -d --build
```

服务端口可覆盖：`MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT`。

Compose 内置：

- MySQL/Redis 健康检查
- 后端通过 `CALLBACK_BASE_URL` 指向容器内服务
- 前端 Nginx 反向代理 `/api` 和 `/uploads`

Docker Hub 拉取超时时，可先用 DaoCloud 镜像源拉取并打标准标签。

## 3. Kubernetes

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

生产环境建议替换镜像地址、使用 Secrets 保存密码、配置 HPA 与 HTTPS Ingress。

## 4. 可观测性

- 后端健康检查：`/actuator/health`
- Prometheus 指标：`/actuator/prometheus`
- AI 指标：`/api/v1/metrics`
- 结构化日志包含 `requestId/traceId`

## 5. 运维脚本

```powershell
scripts/start-dev.ps1
scripts/stop-dev.ps1
scripts/smoke.ps1
scripts/backup.ps1
scripts/restore.ps1
scripts/load-test.ps1
scripts/fetch-openapi.ps1
```

## 6. 生产检查项

- 修改 `JWT_SECRET`、MySQL/Redis 密码、AI 回调 Token
- 开启 HTTPS 和 WAF
- 配置 Prometheus + Grafana 告警
- 定期备份并演练恢复
- 所有数据库变更通过 Flyway 执行

