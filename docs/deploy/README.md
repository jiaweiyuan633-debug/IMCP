# 部署总览

本仓库提供两条部署路径，生产以 Kubernetes（Helm）为唯一来源：

1. **本地 Docker 全栈**（`docker-compose.yml`，仓库根目录）：开发/演示用，不参与生产。
2. **Kubernetes**（`k8s/helm/admin-scaffold` Chart）：生产部署的唯一声明来源。

## 1. 两条路径的关系与差异

| 维度 | 本地 Docker 栈 | K8s（Helm） |
| --- | --- | --- |
| 配置层 | 显式注入 `SPRING_PROFILES_ACTIVE=dev`（application.yml 默认 prod） | backend 强制 `SPRING_PROFILES_ACTIVE=prod` |
| 密钥 | dev 明文兜底密钥（仅限本地） | fail-fast：5 个密钥经 Secret 注入，缺失渲染即失败 |
| 流量入口 | 无 Ingress：`docker/nginx.conf` 挂载到 frontend 容器，把 `/api`、`/files`、`/uploads`、`/ws` 反代到 backend（SSE 关缓冲） | Ingress：`/api`、`/files`、`/uploads`、`/ws` → backend，`/` → frontend |
| 上传持久化 | Docker volume `uploads-data`（挂到 backend `/app/uploads`） | PVC `{release}-upload`（默认 RWX，挂到 `/data/uploads`） |
| 伸缩/可用性 | 单副本，无 HPA/PDB | HPA + PDB + 多副本打散 + 优雅停机 |
| 数据库/Redis | compose 内 MySQL 8 / Redis 7 容器 | 外部（云 RDS/托管 Redis 或自建），`config.dbHost/redisHost` 指向实际地址 |
| 部署方式 | `docker compose up -d --build` | `helm upgrade --install` 或 ArgoCD |

生产部署文档见 [`../../k8s/README.md`](../../k8s/README.md)；上线前集群点检手册见 [`cluster-go-nogo.md`](cluster-go-nogo.md)。

## 2. 环境分层

后端通过 `SPRING_PROFILES_ACTIVE` 切换环境，配置层位于 `backend/src/main/resources/application-{env}.yml`：

| 环境 | Profile | 定位 | 接口文档 | 密钥策略 | 采样率 |
| --- | --- | --- | --- | --- | --- |
| dev | `dev` | 本地开发 | 开启 | 明文默认密钥（仅限本地） | 1.0 |
| test | `test` | 联调 / QA / CI 基线 | 开启 | fail-fast，必须注入 | 1.0 |
| prod | `prod` | 生产 | 关闭 | fail-fast，必须注入 | 0.1 |

- `application.yml` 默认激活 **prod**（未注入密钥即启动失败）；本地开发须显式 `SPRING_PROFILES_ACTIVE=dev`（dev 层提供兜底密钥并开 swagger、Flyway 允许 baseline）。
- prod 与 test 差异：关闭 springdoc/knife4j 接口文档、健康详情（`show-details: never`）、采样率降为 0.1；`baseline-on-migrate: false`。

## 3. 本地 Docker 栈

```bash
docker compose up -d --build        # mysql/redis/backend/ai-service/frontend/website
docker compose --profile monitoring up -d zipkin   # 可选：链路追踪
docker compose --profile milvus up -d              # 可选：向量检索（Milvus）
```

- 端口：frontend `8088`、website `8081`、backend `8080`、ai-service `8000`；MySQL 主机 3306、Redis 主机 6380（避免与本机原生服务冲突，容器内互访走 `mysql:3306` / `redis:6379`）。
- frontend 容器挂载 `docker/nginx.conf` 覆盖代理：`/api/`（关缓冲、`proxy_read_timeout 300s` 支持 SSE）、`/uploads/`、`/files/`、`/ws/` 反代到 backend，路径集合与 K8s Ingress 对齐。
- 上传文件落在 volume `uploads-data`（容器内 `/app/uploads`），容器重建不丢。
- 该 compose 仅供本地；生产声明以 Helm 为唯一来源。

### 3.1 本地非容器启动

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
cd ai-service && cp .env.example .env && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev
cd website && pnpm install && pnpm dev --port 5174
```

Windows 可直接运行 `scripts/start-dev.ps1`（对应停止脚本 `scripts/stop-dev.ps1`）。管理端/官网构建时自动生成 PWA 资源（`manifest.webmanifest` / `sw.js`，首次访问后支持离线导航与静态资源缓存）。

## 4. 环境变量清单

变量定义以 **`backend/.env.example`、`ai-service/.env.example` 与 `application-*.yml` 中的占位符**为准，下表为必配/常用核心项。

### 4.1 后端（backend）

| 变量 | 默认/必填 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod`（默认） | dev/test 显式注入 |
| `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` | prod 必填 | MySQL 连接（prod 层无默认值，缺失启动失败） |
| `REDIS_HOST/REDIS_PORT`（可选 `REDIS_PASSWORD/REDIS_DATABASE`） | prod 必填 host/port | Redis 连接 |
| `JWT_SECRET` | 必填 | ≥32 位随机串；非 dev 缺失或等于开发默认密钥时启动失败 |
| `TOTP_ENCRYPTION_KEY` | 必填 | TOTP 加密密钥，禁止复用 JWT_SECRET |
| `MCP_AUTH_TOKEN` | 必填（prod） | MCP Server 端点鉴权令牌 |
| `CALLBACK_BASE_URL` | 本地 `http://127.0.0.1:8080` | AI 回调可达的后端地址；默认仅限本地联调，生产必须为后端实际地址 |
| `FRONTEND_URL` | 本地 `http://localhost:5173` | 前端地址（消息/通知里生成的前端链接用） |
| `AI_BASE_URL` | 空 | ai-service 地址 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | dev 为本地端口列表；**prod 缺省空串** | 逗号分隔前端域名（可含 `*`）；prod 未配置即拒绝全部跨域来源 |
| `AUTH_TOKEN`（Helm 键名 `aiAuthToken`） | 必填 | AI 服务鉴权 Token，须与后端 `AiServiceConfig.apiKey` 一致 |
| `REDIS_SENTINEL_MASTER/NODES/PASSWORD` | 空 | 可选：主从哨兵拓扑（master 与 nodes 成对注入） |
| `ZIPKIN_ENDPOINT` | 空 | 链路追踪上报端点；空即关闭（`MANAGEMENT_TRACING_ENABLED=false`） |
| `TRACING_SAMPLING_PROBABILITY` | dev 1.0 / prod 0.1 | 采样率 |
| `STORAGE_TYPE/MINIO_*` | local | 切换 MinIO 对象存储时配置 |
| `SQL_LOG_THRESHOLD_MS` / `AI_SCAN_INTERVAL_MS` | 50 / 30000 | SQL 慢日志阈值 / AI 任务扫描间隔 |
| 其余可选键 | — | 分片清理、报表行数上限、SSE/WS 连接上限、密码策略等，见 `application.yml` 占位符 |

### 4.2 AI 服务（ai-service）

| 变量 | 默认/必填 | 说明 |
| --- | --- | --- |
| `AUTH_TOKEN` | 必填 | 入站 Bearer + 出站回调 HMAC 共用；代码无默认值，未注入即启动失败 |
| `REDIS_URL` | `redis://localhost:6379/0` | 任务队列/向量存储 |
| `CALLBACK_ALLOWED_ORIGINS` | `[]`（仅允许 localhost） | JSON 数组；出站任务回调 origin 白名单，生产必须显式注入后端可达 origin |
| `LLM_PROVIDERS` / `LLM_DEFAULT_PROVIDER` | `{}` / `mock` | 真实大模型提供方 JSON；留空则以 mock 运行 |
| `WORKER_COUNT` | `2` | 每副本工作协程数 |
| `OCR_PROVIDER` / `OCR_FAIL_FAST` | `mock` / `false` | OCR 提供方（tesseract 需镜像内置） |
| `MAX_TIMEOUT_SECONDS` / `DEFAULT_TIMEOUT_SECONDS` | 3600 / 60 | 任务超时上限与默认值 |

## 5. 生产必配（对照检查）

- **密钥**：替换 `JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、MySQL/Redis 口令、`AUTH_TOKEN`（与后端 `AiServiceConfig.apiKey` 一致）、`MCP_AUTH_TOKEN`。Helm 场景经 `--set secret.*` 或外部 Secret（`secret.existingSecret`，配合 ESO/Vault/Sealed Secrets）注入。
- **回调地址**：`CALLBACK_BASE_URL` 配置为 AI 服务可访问的后端地址（默认 `127.0.0.1` 仅限本地联调），否则 AI 任务完成回调无法到达后端。Helm 下 `config.callbackBaseUrl` 留空时由模板按 release 名推导为 `http://{release}-backend:8080`；非默认 release 名或自定义 Service 名须显式覆盖。
- **CORS**：`CORS_ALLOWED_ORIGIN_PATTERNS`（逗号分隔前端域名模式）注入后端允许来源。生产配置层缺省为空串即**拒绝全部跨域来源**，未配置时后台前端浏览器 API 请求返回 `403 Invalid CORS request`。Helm 经 `config.corsAllowedOriginPatterns` 注入（values 默认占位 `https://admin.example.com`），须覆盖为实际前端域名（与 `ingress.host` 一致）。
- **AI 回调 SSRF 白名单**：`CALLBACK_ALLOWED_ORIGINS`（JSON 数组）限定 AI 出站回调允许的 origin；未配置时仅允许 localhost 回环（仅限后端与 AI 同机联调）。生产多机/容器部署必须显式注入后端可达的 origin，否则任务完成回调被 AI 侧拒绝（任务仍终态，但后端收不到通知）。云元数据（169.254.169.254）与链路本地/保留地址无论白名单如何一律拒绝。Helm 下由模板从 `callbackBaseUrl` 自动推导。
- **HTTPS**：生产启用 HTTPS（Ingress TLS，cert-manager 自动签发或云 LB 终止）+ WAF；强制 HTTP→HTTPS 跳转（Chart `ingress.forceHttps=true`，对应 ingress-nginx `force-ssl-redirect`）。HTTPS 下后端 refresh token cookie 默认带 `Secure`（prod 层 `REFRESH_COOKIE_SECURE` 缺省 true）。官网域名与后台域名分离，启用 HTTPS 后配置 CDN 与转化埋点。
- **持久化**：K8s 多副本上传卷必须 RWX 存储类；或按需切换 MinIO 对象存储（`STORAGE_TYPE=minio` + `app.storage.minio.*`，容器内自行注入）。对象存储建议配置生命周期策略。
- **可观测性**：Prometheus 采集 backend `/actuator/prometheus` 与 **ai `/metrics`（根路径）**，Grafana 看板与告警规则见 `k8s/monitoring/`；日志采集到 Loki/ELK（prod 控制台输出 JSON 单行日志）。
- **备份**：定期 `scripts/backup.ps1` + 异地副本 + 至少每季度一次 `scripts/backup-drill.ps1` 演练（RPO/RTO 留记录）。
- **数据库变更**：全部经 Flyway 迁移（纪律见 [`../database/README.md`](../database/README.md)）。

## 6. 运维脚本

```text
scripts/start-dev.ps1       # 本地全栈启动
scripts/stop-dev.ps1        # 本地全栈停止
scripts/smoke.ps1           # 冒烟
scripts/backup.ps1          # 备份
scripts/restore.ps1         # 恢复
scripts/backup-drill.ps1    # 备份恢复演练
scripts/load-test.ps1       # 压测
scripts/load-test-multi.ps1 # 多用户压测
scripts/fetch-openapi.ps1   # 拉取接口文档
scripts/verify-cluster.ps1  # 集群点检半自动预检（配合上线检查手册）
```
