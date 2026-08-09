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

PWA 离线缓存：

- 后台管理系统和官网构建时自动生成 `manifest.webmanifest` 与 `sw.js`
- 首次访问后支持离线导航和静态资源缓存

## 2. Kubernetes

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

Chart 默认部署 backend/ai/frontend 各 2 副本，backend 带 HPA（CPU 70%，2-6 副本）和 Ingress。官网如需独立 K8s 服务，也可按相同模板扩展。生产环境建议替换镜像地址、使用云数据库或托管 Redis，并通过 Secret 保存密码。

## 3. 可观测性

- 后端健康检查：`/actuator/health`（Kubernetes 探针细分使用 `/actuator/health/readiness` 与 `/actuator/health/liveness`）
- Prometheus 指标：`/actuator/prometheus`
- AI 指标：`/api/v1/metrics`
- 结构化日志包含 `requestId/traceId`
- 监控页面：服务器监控、SQL 监控、登录/操作日志、定时任务日志
- 告警规则：CPU/内存/JVM/磁盘阈值触发后写入通知公告，并通过 SSE 实时推送
- 审计日志：操作参数与结果自动落库，可在后台审计日志页查询

## 4. 运维脚本

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

## 5. 生产检查项

- 修改 `JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、MySQL/Redis 密码、AI 回调 Token
- `CALLBACK_BASE_URL` 配置为 AI 服务可访问的地址（默认 `127.0.0.1` 仅限本地联调），否则 AI 回调无法到达后端
- 按需启用 MinIO 对象存储并配置生命周期策略
- 开启 HTTPS 和 WAF
- 配置 Prometheus + Grafana 告警
- 定期备份并演练恢复
- 所有数据库变更通过 Flyway 执行
- 当前数据库版本 V1-V33，升级时避免直接修改已执行迁移脚本
- 多租户生产环境前确认租户标识传递、数据权限与备份粒度策略
- 官网域名与后台域名分离，启用 HTTPS 后配置 CDN 与转化埋点

