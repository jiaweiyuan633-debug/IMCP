# 部署教程

## 环境分层

后端通过 `SPRING_PROFILES_ACTIVE` 切换环境，配置层位于 `backend/src/main/resources/application-{env}.yml`：

| 环境 | Profile | 定位 | 接口文档 | 密钥策略 | 采样率 |
| --- | --- | --- | --- | --- | --- |
| dev | `dev`（默认） | 本地开发 | 开启 | 明文默认密钥（仅限本地） | 1.0 |
| test | `test` | 联调 / QA / CI 基线 | 开启 | fail-fast，必须注入 | 1.0 |
| prod | `prod` | 生产 | 关闭 | fail-fast，必须注入 | 0.1 |

- dev：默认激活，无需显式指定；依赖本地 MySQL/Redis，缺省密钥走 dev 明文兜底。
- test：`SPRING_PROFILES_ACTIVE=test`，数据源默认 `admin_scaffold_test`（与本地开发库隔离）；`JWT_SECRET / TOTP_ENCRYPTION_KEY / MCP_AUTH_TOKEN` 必须注入，缺失即启动失败。
- prod：`SPRING_PROFILES_ACTIVE=prod`，见第 5 节生产检查项；与 test 的差异是关闭接口文档、健康详情与采样率降至 0.1。

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

后端通过环境变量连接基础设施（本地开发默认加载 `dev` profile，密钥缺省时使用开发兜底；**生产必须注入 `SPRING_PROFILES_ACTIVE=prod`，缺省/默认密钥会导致启动失败**）：

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
# 必填：≥32 位随机串；生产缺失或等于开发默认密钥时启动失败
JWT_SECRET=
# 必填：TOTP 加密密钥，禁止复用 JWT_SECRET
TOTP_ENCRYPTION_KEY=
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

> **Helm Chart（`k8s/helm/admin-scaffold`）是 K8s 部署的唯一来源**。原生清单（`k8s/manifests.yaml` / `k8s/configmap.yaml`）已移除，避免与 Chart 漂移。生产环境经 ArgoCD（`gitops/argocd/application.yaml`）声明式同步，或直接执行：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace \
  --set images.backend=registry.example.com/admin-backend:1.0.0 \
  --set images.ai=registry.example.com/ai-service:1.0.0 \
  --set images.frontend=registry.example.com/admin-frontend:1.0.0 \
  --set images.website=registry.example.com/website:1.0.0 \
  --set config.dbHost=mysql \
  --set secret.dbPassword='<强口令>' \
  --set secret.jwtSecret='<≥32 位随机串>' \
  --set secret.totpEncryptionKey='<随机串>' \
  --set secret.aiAuthToken='<随机串，与后端 AiServiceConfig.apiKey 保持一致>' \
  --set secret.mcpAuthToken='<随机串，MCP Server 端点鉴权令牌>' \
  --set ingress.host=admin.example.com
```

密钥必须通过 `--set secret.*` 显式注入（values 默认留空，未注入时模板顶部 `fail` 校验直接终止渲染，杜绝明文默认密钥上生产）。Chart 默认部署 backend/ai/frontend/website 各 2 副本，backend/ai 带 HPA、PDB 与 NetworkPolicy。高可用项（P2-15）：上传卷默认挂 PVC（`storage.enabled=true`，多副本需 ReadWriteMany 存储类）、可选用 Redis 主从哨兵（`--set config.redisSentinelMaster=... --set config.redisSentinelNodes=...`，成对注入）、backend/ai 多副本打散、prod 优雅停机（30s 关闭超时）。生产环境建议替换镜像地址、使用云数据库或托管 Redis，并通过外部 Secret（Vault / External Secrets / Sealed Secrets）注入密钥。

## 3. 可观测性

### 指标采集（Prometheus）

- 后端健康检查：`/actuator/health`（Kubernetes 探针细分使用 `/actuator/health/readiness` 与 `/actuator/health/liveness`）
- 后端指标：`/actuator/prometheus`（Micrometer，指标带 `application=admin-backend` 标签）
- AI 指标：`/api/v1/metrics`
- 采集配置与告警规则位于 [k8s/monitoring/](../../k8s/monitoring/)：
  - `prometheus.yml` —— 抓取 admin-backend / ai-service，Redis/MySQL exporter 按需启用
  - `prometheus-rules.yml` —— 服务宕机、5xx 错误率、P95 延迟、JVM 堆、Tomcat 线程、Redis 内存等告警
  - `alertmanager.yml` —— 告警收敛路由与接收人占位（钉钉/企微/邮件等按需填写）

### 日志（Loki / ELK）

- 结构化日志包含 `requestId/traceId/spanId`
- **prod profile 下控制台输出 JSON 单行日志**（LogstashEncoder），由容器运行时 + promtail/filebeat/fluent-bit 直接采集，无需挂载日志卷；同时保留滚动 JSON 文件（`logs/admin-json.log`）供历史归档
- dev profile 控制台保持人类可读

### 监控业务面

- 监控页面：服务器监控、SQL 监控、登录/操作日志、定时任务日志
- 告警规则：CPU/内存/JVM/磁盘阈值触发后写入通知公告，并通过 SSE 实时推送
- 审计日志：操作参数与结果自动落库，可在后台审计日志页查询

### 链路追踪

- Micrometer Tracing（Brave），采样率：dev 默认 1.0，prod 默认 0.1（`TRACING_SAMPLING_PROBABILITY` 可覆盖）；配置 `ZIPKIN_ENDPOINT` 后可上报 Zipkin

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

- 修改 `JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、MySQL/Redis 密码、AI 服务鉴权 Token（`AUTH_TOKEN`，须与后端 `AiServiceConfig.apiKey` 保持一致）、MCP Server 鉴权令牌（`MCP_AUTH_TOKEN`）
- `CALLBACK_BASE_URL` 配置为 AI 服务可访问的地址（默认 `127.0.0.1` 仅限本地联调），否则 AI 回调无法到达后端
- 按需启用 MinIO 对象存储并配置生命周期策略
- 开启 HTTPS 和 WAF
- 配置 Prometheus + Grafana 告警
- 定期备份并演练恢复
- 所有数据库变更通过 Flyway 执行
- 当前数据库版本 V1-V52，升级时避免直接修改已执行迁移脚本
- 多租户生产环境前确认租户标识传递、数据权限与备份粒度策略
- 官网域名与后台域名分离，启用 HTTPS 后配置 CDN 与转化埋点
