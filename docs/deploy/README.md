# 部署教程

## 环境分层

后端通过 `SPRING_PROFILES_ACTIVE` 切换环境，配置层位于 `backend/src/main/resources/application-{env}.yml`：

| 环境 | Profile | 定位 | 接口文档 | 密钥策略 | 采样率 |
| --- | --- | --- | --- | --- | --- |
| dev | `dev` | 本地开发 | 开启 | 明文默认密钥（仅限本地） | 1.0 |
| test | `test` | 联调 / QA / CI 基线 | 开启 | fail-fast，必须注入 | 1.0 |
| prod | `prod` | 生产 | 关闭 | fail-fast，必须注入 | 0.1 |

- dev：application.yml 默认 prod（未注入密钥即启动失败），本地开发须显式指定 `SPRING_PROFILES_ACTIVE=dev`；依赖本地 MySQL/Redis，缺省密钥走 dev 明文兜底。
- test：`SPRING_PROFILES_ACTIVE=test`，数据源默认 `admin_scaffold_test`（与本地开发库隔离）；`JWT_SECRET / TOTP_ENCRYPTION_KEY / MCP_AUTH_TOKEN` 必须注入，缺失即启动失败。
- prod：`SPRING_PROFILES_ACTIVE=prod`，见第 5 节生产检查项；与 test 的差异是关闭接口文档、健康详情与采样率降至 0.1。

## 1. 本地开发

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
cd ai-service && cp .env.example .env && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev
cd website && pnpm install && pnpm dev --port 5174
```

也可以在 Windows 上直接运行：

```powershell
scripts/start-dev.ps1
```

后端通过环境变量连接基础设施（application.yml 默认 `prod`，本地开发必须注入 `SPRING_PROFILES_ACTIVE=dev` 以启用 swagger 与开发兜底密钥；**默认 profile 为 prod，未注入密钥即启动失败，杜绝静默携带公开 dev 密钥上线**）：

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
- AI 指标：`/metrics`（根路径；批次5·R4-1.51 修正了此前误述的 `/api/v1/metrics`——与代码不符会抓取 404）
- **Prometheus 本体需预先部署**：本仓库仅提供采集/告警配置与 Grafana 数据源，不含 Prometheus 服务端清单。本地演示：`docker run -d --name prometheus -p 9090:9090 -v "$(pwd)/k8s/monitoring:/etc/prometheus:ro" prom/prometheus --config.file=/etc/prometheus/prometheus.yml`；生产：`helm repo add prometheus-community https://prometheus-community.github.io/helm-charts && helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring`。
- **命名空间对齐（关键）**：监控栈（Prometheus + Grafana）部署于 `monitoring` 命名空间，`prometheus.yml` 抓取目标用全限定名跨命名空间指向 `admin-scaffold` 命名空间的业务 Service（`admin-scaffold-backend.admin-scaffold.svc.cluster.local:8080` / `admin-scaffold-ai.admin-scaffold.svc.cluster.local:8000`）。若 Helm release 或命名空间不同，须同步修改 `prometheus.yml` 的 targets FQDN。
- 采集配置与告警规则位于 [k8s/monitoring/](../../k8s/monitoring/)：
  - `prometheus.yml` —— 抓取 admin-backend / ai-service（FQDN 目标），Redis/MySQL exporter 按需启用
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

### 链路追踪（Zipkin）

- Micrometer Tracing（Brave），采样率：dev 默认 1.0，prod 默认 0.1（`TRACING_SAMPLING_PROBABILITY` 可覆盖）
- **本地**：`docker compose --profile monitoring up -d zipkin` 启动 Zipkin（端口 9411），后端默认注入 `ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans`；访问 `http://localhost:9411` 查看调用链
- **生产**：Helm 设 `--set config.zipkinEndpoint=http://<zipkin>/api/v2/spans` 注入上报端点；未配置时 Chart 注入 `MANAGEMENT_TRACING_ENABLED=false` 显式关闭 tracing（空 endpoint 下 Zipkin 自动装配仍会实例化 Sender/SpanHandler、每次 flush 失败丢 span），配置端点后注入 `true` + `ZIPKIN_ENDPOINT` 开启上报
- 结构化日志字段含 `traceId/spanId`，可与 Zipkin 联动检索

### 可视化（Grafana）

- `k8s/monitoring/grafana/` 提供 Grafana Deployment + 预置 Prometheus 数据源 + 平台总览 dashboard（后端 JVM/HTTP/连接池、AI 服务进程指标），按该目录 `README.md` 部署（含创建 `monitoring` 命名空间；Prometheus 需预先部署于该命名空间）
- 指标来源：后端 `/actuator/prometheus`、AI 服务 `/metrics`（`prometheus.yml` 的 ai-service job 已修正为此路径）

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
- `CALLBACK_BASE_URL` 配置为 AI 服务可访问的地址（默认 `127.0.0.1` 仅限本地联调），否则 AI 回调无法到达后端；Helm 部署时 `config.callbackBaseUrl` / `config.aiBaseUrl` / `config.frontendUrl` 留空由模板按 `.Release.Name` 推导（`{release}-backend` / `{release}-ai` / `{release}-frontend`），非默认 release 名或自定义 Service 名须显式覆盖（R4-1.45）
- **CORS 生产必配**：`CORS_ALLOWED_ORIGIN_PATTERNS`（逗号分隔的前端域名模式，可含 `*` 通配）注入后端 CORS 允许来源。生产配置层（application-prod.yml）缺省为空串即**拒绝全部跨域来源**，未配置时后台前端经浏览器发起的 API 请求会返回 `403 Invalid CORS request`；Helm 部署经 `config.corsAllowedOriginPatterns` 注入（values 默认 `https://admin.example.com`），自定义域名或新增前端源须显式覆盖为实际域名列表（R4-1.45 起兜底收紧为缺省拒绝）
- **AI 回调 SSRF 白名单**：AI 服务出站任务回调仅允许打到 `CALLBACK_ALLOWED_ORIGINS`（JSON 数组，Helm 已从 `config.callbackBaseUrl` 自动推导）列出的 origin，未配置时仅允许 localhost 回环（仅限后端与 AI 同机联调）；生产多机/容器部署必须显式注入后端可达的 origin，否则任务完成回调会被 AI 侧拒绝（任务仍终态，但后端收不到通知）。云元数据（169.254.169.254）与链路本地/保留地址无论白名单如何一律拒绝
- 按需启用 MinIO 对象存储并配置生命周期策略
- 开启 HTTPS 和 WAF
- 配置 Prometheus + Grafana 告警
- 定期备份并演练恢复
- 所有数据库变更通过 Flyway 执行
- 当前数据库版本 V1-V62，升级时避免直接修改已执行迁移脚本
- 多租户生产环境前确认租户标识传递、数据权限与备份粒度策略
- 官网域名与后台域名分离，启用 HTTPS 后配置 CDN 与转化埋点
