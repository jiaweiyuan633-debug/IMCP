# 部署教程

## 1. 本地开发

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

```bash
cd backend && mvn spring-boot:run
cd ai-service && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev
cd website && pnpm install && pnpm dev --port 5174
```

也可以在 Windows 上直接运行：

```powershell
scripts/start-dev.ps1
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
JWT_SECRET=change-me
SQL_LOG_THRESHOLD_MS=50
AI_SCAN_INTERVAL_MS=30000
```

文件存储默认使用本地目录 `uploads/`；切换 MinIO 时配置：

```text
STORAGE_TYPE=minio
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=admin
UPLOAD_PATH=uploads
```

## 2. Docker Compose

```bash
cd docker
docker compose up -d --build
```

Compose 编排六个服务：MySQL 8、Redis 7、Java 后端、AI 服务、后台管理系统、Y15智能管理平台官网。

服务端口可覆盖：`MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT`、`WEBSITE_PORT`。

Compose 内置：

- MySQL/Redis 健康检查
- 后端通过 `CALLBACK_BASE_URL` 指向容器内 AI 回调地址
- 前端 Nginx 反向代理 `/api` 和 `/uploads`
- 官网使用独立 Nginx 容器，默认端口 `8081`

可观测性按需启用：

```bash
docker compose -f docker-compose.yml -f observability.yml up -d
```

- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`（默认 `admin/admin123`）
- Loki：`http://localhost:3100`
- Tempo：`http://localhost:3200`
- 后端链路追踪：通过 Brave 上报 Tempo，`ZIPKIN_ENDPOINT` 指向 Tempo 9411

Docker Hub 拉取超时时，可先用 DaoCloud 镜像源拉取并打标准标签。

## 3. Kubernetes

### 原生清单

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

### Helm Chart

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace \
  --set config.dbHost=mysql \
  --set secret.dbPassword=root123456 \
  --set secret.jwtSecret=change-me \
  --set ingress.host=admin.example.com
```

Chart 默认部署 backend/ai/frontend 各 2 副本，backend 带 HPA（CPU 70%，2-6 副本）和 Ingress。官网当前通过 Docker 独立交付，也可按相同模板扩展 K8s 服务。生产环境建议替换镜像地址、使用云数据库或托管 Redis，并通过 Secret 保存密码。

## 4. 可观测性

- 后端健康检查：`/actuator/health`
- Prometheus 指标：`/actuator/prometheus`
- AI 指标：`/api/v1/metrics`
- 结构化日志包含 `requestId/traceId`
- 监控页面：服务器监控、SQL 监控、登录/操作日志、定时任务日志
- 告警规则：CPU/内存/JVM/磁盘阈值触发后写入通知公告，并通过 SSE 实时推送
- 审计日志：操作参数与结果自动落库，可在后台审计日志页查询

## 5. 运维脚本

```powershell
scripts/start-dev.ps1
scripts/stop-dev.ps1
scripts/smoke.ps1
scripts/backup.ps1
scripts/restore.ps1
scripts/backup-drill.ps1
scripts/load-test.ps1
scripts/load-test-multi.ps1
scripts/fetch-openapi.ps1
```

## 6. 生产检查项

- 修改 `JWT_SECRET`、MySQL/Redis 密码、AI 回调 Token
- 按需启用 MinIO 对象存储并配置生命周期策略
- 开启 HTTPS 和 WAF
- 配置 Prometheus + Grafana 告警
- 定期备份并演练恢复
- 所有数据库变更通过 Flyway 执行
- 当前数据库版本 V1-V19，升级时避免直接修改已执行迁移脚本
- 多租户生产环境前确认租户标识传递、数据权限与备份粒度策略
- 官网域名与后台域名分离，启用 HTTPS 后配置 CDN 与转化埋点

