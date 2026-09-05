# 运维手册（Runbook）

面向**部署与发布负责人 / SRE / 值班运维**。命令以仓库 `scripts/` 下的 PowerShell
脚本为准，生产形态以 Helm Chart `k8s/helm/admin-scaffold` 与 GitOps 清单
（`gitops/argocd`）为准；版本号、迁移序号、镜像标签等信息不在此重复罗列，一律以
`pom.xml` / `package.json` / `db/migration` 目录 / Chart `values.yaml` 等实际文件为
最新事实源。

## 常用命令

| 场景 | 命令 | 说明 |
| --- | --- | --- |
| 本地启动 | `scripts/start-dev.ps1` | 拉起 backend / ai-service / frontend / website（可传 `-BackendPort`、`-AiPort` 等），日志在 `logs/` |
| 本地停止 | `scripts/stop-dev.ps1` | 按 `.runtime/*.pid` 停止上述进程 |
| 冒烟验证 | `scripts/smoke.ps1 [-BaseUrl http://127.0.0.1:8080]` | 登录后对后端各模块关键接口做冒烟，输出 PASS/FAIL 汇总 |
| 压测（登录） | `scripts/load-test.ps1 [-Concurrency 10] [-Rounds 20]` | 并发登录压测，打印成功率与耗时 |
| 压测（多接口） | `scripts/load-test-multi.ps1 [-Concurrency 5] [-Rounds 10]` | 登录 + 用户/监控/文件/告警/流程/AI 等多接口混合压测 |
| 集群预检 | `scripts/verify-cluster.ps1` | 目标集群上线前 Go/No-Go 检查（metrics-server、RWX 存储、Ingress、cert-manager、monitoring 等，见 `docs/deploy/cluster-go-nogo.md`） |
| 备份 / 恢复 / 演练 | `scripts/backup.ps1`、`scripts/restore.ps1`、`scripts/backup-drill.ps1` | 见「备份与恢复」 |
| 契约刷新 | `scripts/fetch-openapi.ps1` | 抓取 `/v3/api-docs` 到 `docs/api/openapi.json`（见 `docs/api/openapi.md`） |

## 构建、测试与 CI

### Java 后端

- 单元测试（surefire，无外部依赖）：`cd backend && mvn test`。
- 全量验证：`cd backend && mvn verify`。该命令执行 Testcontainers 集成测试
  （`*IT` 后缀类，MySQL + Redis 容器 + Flyway 迁移）并通过多道质量门禁：
  - **JaCoCo 覆盖率门槛**：LINE / BRANCH / METHOD 三档 `COVEREDRATIO` 检查，
    数值以 `backend/pom.xml` 的 jacoco `check` 配置为准（当前
    LINE≥0.40 / BRANCH≥0.36 / METHOD≥0.37）；新增未测代码会使门禁失败；
  - **SpotBugs**：`failOnError=true`，存量问题经 `backend/spotbugs-exclude.xml`
    白名单管理，新增未豁免 bug 即失败；
  - 无有效 Docker 环境时 `*IT` 自动跳过（`DockerExecutionCondition`），不阻塞构建；
    集成测试基类以 JVM 级单例 MySQL + Redis 容器共享 Spring 上下文，规避 Windows
    Docker 反复建删容器的端口映射抖动。
- **Windows + Docker Desktop 注意**：Docker Desktop 的 named pipe 要求 Docker API
  ≥ v1.44 且实现 bootstrap redirect，而 docker-java 默认协商 v1.32 且不跟随重定向，
  可能导致 `*IT` 全部 Skipped（`BadRequest 400`）。修复：在用户目录
  `~/.docker-java.properties` 写入 `api.version=1.44`（仅本机生效，不影响 CI）。

### Python AI 服务

`cd ai-service && uv run pytest -q`（或 `./.venv/Scripts/python.exe -m pytest -q`）。
测试使用 `fakeredis` 与确定性 `mock` LLM，可离线运行；覆盖率门禁配置在
`ai-service/pyproject.toml` 的 pytest `addopts`（`--cov-fail-under=…`）。

### CI 与发布门禁

GitHub Actions（`.github/workflows/ci.yml`）每次提交执行：后端 `mvn verify`、前端
lint / Vitest 覆盖率测试（`frontend/vitest.config.ts`，阈值以文件为准）/ build、
AI pytest 覆盖率与 ruff、Website build、Playwright E2E（docker compose 起栈）、
Gitleaks / Trivy / CodeQL 安全扫描，并按需构建推送镜像。合并主干的代码应保证这些
门禁全绿。

## 数据库与 Flyway

- 迁移目录：`backend/src/main/resources/db/migration`，命名 `V{n}__description.sql`；
  最新迁移序号以目录中的最大 V 序号为准（规则与既有迁移规范见 `docs/database/README.md`）。
- **禁止修改已执行过的迁移脚本**；任何 schema/数据变更必须新增 V 系列脚本
  （Flyway 启动时自动执行，先于应用接受流量）。
- 应用启动即跑迁移：迁移失败会导致服务启动失败（fail-fast），不要用重启掩盖。
- 备份/演练纪律见下节；涉及大版本迁移的发布流程见「版本升级与回滚」。

### Flyway 失败处置

1. **先看日志**：启动失败时读取 backend 日志中 Flyway 报错，定位失败语句与当前
   schema 基线（哪条 V 失败、是否为新加脚本）。
2. **分类处理**：
   - 新脚本本身有误且尚未在任何正式环境执行 → 修正脚本；开发环境可直接重建库
     （或先 `flyway repair` 清除失败版本记录再重跑修正后的脚本）。
   - 已在多环境执行、线上失败 → **不要改历史脚本**。MySQL 的 DDL 多数不可回滚，
     部分执行不会自动还原：需要人工比对目标 schema 修正库，再以新 V 脚本补齐
     差异，而不是编辑旧脚本重跑。
   - 依赖环境差异（字符集、权限、外部对象）→ 核对连接账号权限与 SQL 方言。
3. **纪律**：永远不要手工执行 SQL “绕过”版本记录，否则后续迁移会与真实 schema
   错位；大版本迁移上线前先在预发布环境全量演练（含 `backup-drill` 类恢复验证）。

## 备份与恢复

- **`scripts/backup.ps1`** 输出到 `backups/<db>_<时间戳>/`（单次备份统一目录，便于
  归档）：MySQL 逻辑全量（`mysqldump --single-transaction --routines --triggers`，
  口令经 `MYSQL_PWD` 环境变量注入、`--result-file` 直落盘，避免命令行明文与管道
  编码问题）+ Redis RDB 快照（`redis-cli --rdb`，口令经 `REDISCLI_AUTH` 注入，
  `-SkipRedis` 可关）+ MinIO 桶镜像（提供 `-MinioEndpoint/-MinioAccessKey/
  -MinioSecretKey/-MinioBucket` 后以 `mc mirror` 执行，`-SkipMinio` 可关）。
  主要参数：`-DbHost/-DbUser/-DbPassword/-DbName/-OutputDir/-MySqlBin` 等。
- **`scripts/restore.ps1`**：`-BackupFile <文件>` 恢复 MySQL，默认执行恢复校验
  （表数量 + 关键表抽查），`-SkipVerify` 关闭；Redis RDB 恢复需停机替换 dump.rdb
  （脚本只给出手动步骤，不自动执行）；MinIO 通过 `mc mirror` 反向回写。
- **Redis 恢复注意**：RDB 恢复会覆盖目标实例全量数据，执行前确认目标实例与备份
  来源一致（同环境、同用途）。
- 备份含业务数据与文件对象，归档、传输与异地存储按敏感数据处理。

### 定时与异地备份（生产必配）

- **定时执行**：Windows 计划任务或 Linux cron 每日调用 `backup.ps1`。示例
  （Linux cron，每日 02:00，口令经 Secret/环境注入，不出现在命令行）：

  ```bash
  0 2 * * * /opt/admin-scaffold/scripts/backup.ps1 -DbHost <db> -DbUser <user> -DbPassword "$(cat /run/secrets/db_pwd)" -OutputDir /backups -SkipRedis 2>> /var/log/backup.log
  ```

- **异地/对象存储**：备份目录定期同步到对象存储（`mc mirror /backups
  s3/bucket/admin-scaffold-backups`），或把 `OutputDir` 直接指向挂载的对象存储目录。
  同机同盘备份在磁盘故障时与主库同毁。
- **时间点恢复（PITR）**：`mysqldump --single-transaction` 只提供“最近一次全量”
  恢复点。需要 PITR 时二选一：① 开启 MySQL binlog（`log_bin=ON`），全量备份后定期
  `mysqlbinlog --start-datetime=... binlog.* > incr.sql` 归档增量；② 使用云 RDS
  自动备份（自带 PITR）。恢复 = 最近全量 + 增量重放至目标时刻。
- **RPO/RTO 目标**：全量每日（RPO≤24h）+ binlog 增量（RPO≤分钟级）；RTO 以最近
  一次 backup-drill 实测为准。
- **演练**：定期执行 `scripts/backup-drill.ps1` 在独立库（`<db>_drill`）恢复验证。
  该演练只覆盖 MySQL 链路（Redis RDB 恢复需停机、MinIO 需 mc 环境，不纳入自动
  演练），跑完自动清理演练库；在生产变更前后都应演练一次。

## 监控、探针与告警

### 监控端点清单

| 服务 | 端点 | 用途 |
| --- | --- | --- |
| backend | `/actuator/prometheus` | Prometheus 抓取（指标带 `application` 标签） |
| backend | `/actuator/health` | 健康检查；K8s 探针用 `/actuator/health/readiness` 与 `/actuator/health/liveness`（`management.endpoint.health.probes.enabled=true`） |
| ai-service | `/livez` | 纯进程存活探针（恒 200，K8s liveness） |
| ai-service | `/readyz` | 就绪探针（依赖 Redis，K8s readiness） |
| ai-service | `/metrics` | Prometheus 指标（**根路径**；业务指标 `ai_*`，抓取时实时采样 `ready/delayed/dead` 队列深度，Redis 不可用时置 `-1`） |
| ai-service | `/health` | 兼容旧客户端的探活（依赖 Redis，不可用返回 503） |
| Grafana | `http://localhost:3000` | 默认本地监控 UI（监控清单在 `k8s/monitoring/`） |

> ai-service 指标挂在根路径而非 API 前缀下——它是基础设施端点，标准 scrape 路径
> 就是根 `/metrics`。若在 Prometheus/Grafana 中配错 scrape 路径会抓不到数据。

### 告警处置对照表

告警规则见 `k8s/monitoring/prometheus-rules.yml`（阈值是脚手架基线，对接真实业务后
应按 SLO 调整；Redis/MySQL 相关规则要求对应 exporter 已部署且 `prometheus.yml`
已启用相应 job，否则规则恒为空、静默漏报）。

| 告警（severity） | 触发 | 影响 | 排查与处置 |
| --- | --- | --- | --- |
| `AdminBackendDown`（critical） | `up{job="admin-backend"}==0` 持续 2m | 后端不可用 | `kubectl -n admin-scaffold get pods`；`kubectl -n admin-scaffold logs deployment/<release>-backend --tail=200`；检查资源/探针/网络策略 |
| `AIServiceDown`（critical） | `up{job="ai-service"}==0` 持续 2m | AI 任务失败或阻塞 | 同上定位 ai-service Pod；AI 依赖 Redis，先确认 Redis 正常 |
| `RedisDown` / `MySqlDown`（critical） | exporter 不可达 2m | 认证/锁/缓存或全站故障 | 检查对应 Pod/云实例；**确认 prometheus.yml 已启用对应 exporter job** |
| `BackendHighErrorRate`（warning） | 5xx 占比 >5%（5m，按 instance/uri 聚合） | 接口故障 | Grafana 按 `application/instance/uri` 定位；查异常堆栈、下游依赖 |
| `BackendHighLatency`（warning） | P95 >1s（5m） | 体验劣化 | 定位慢接口；查慢 SQL（`sys_sql_log`）、外部调用、GC |
| `BackendJvmHeapHighUsage`（warning） | JVM 堆 >90%（5m） | OOM 风险 | dump 分析（`jmap -dump`）；查泄漏；必要时扩容 |
| `BackendThreadsBusy`（warning） | Tomcat 忙线程 >90%（5m） | 请求排队 | 查慢请求/线程泄漏；扩容副本 |
| `RedisMemoryHighUsage`（warning） | Redis >85% maxmemory（5m） | 缓存淘汰、命中率下降 | 查大 key/过期策略；需启用 redis-exporter |
| `PersistentVolumeAlmostFull`（warning） | PVC >85%（5m） | 写入失败 | 清理或扩容 PVC（按存储类能力） |
| `OutboxDeadLetterAccumulating`（warning） | 事务发件箱持续投递失败（15m） | 消息/回调缺失 | 查发件箱表（`sys_outbox`）；检查回调端点/网络 |
| `AiDeadLetterQueueGrowth`（warning） | 1h 内 AI 重试耗尽 >10（10m） | AI 任务失败 | 查死信（AI `GET /api/v1/tasks/dead`）；检查 LLM Provider 配置/配额/任务参数 |

## 常见问题处置

- **登录提示 401**：确认 `JWT_SECRET` 在所有副本一致；检查 Redis 中会话令牌是否被
  清理；如刚完成密钥轮换，属预期（用户重新登录）。
- **AI 任务一直 PENDING**：确认 `AI_BASE_URL` 指向可达的 ai-service、ai-service 的
  `AUTH_TOKEN` 与后端 AI 服务配置 `apiKey` 一致；确认 `CALLBACK_BASE_URL` 能被
  ai-service 回调（ai-service 配置了 `CALLBACK_ALLOWED_ORIGINS` 后白名单须包含后端
  入口）；查 backend 与 ai-service 两侧日志与 `ai_task` 表。
- **AI 任务终态失败**：按回调/任务记录的 `error_type`（`reason`）区分——`timeout`
  属瞬时超时（可重试/加大 timeout），`non_retryable` 属确定性错误（重试无意义，
  排查参数与配置），`retries_exhausted` 属重试耗尽（查 Provider 配额与日志）。
- **通知收不到**：检查 SSE Ticket 是否过期（一次性）；多实例场景确认 Redis 通道
  正常（公告/消息经 Redis pub/sub 广播）。
- **文件访问 403**：文件内容访问需要短期签名 Token，重新上传或通过文件管理页获取
  Token；`/uploads`、`/files` 路径需经反向代理/Ingress 放行。
- **上传 413**：后端 multipart 上限默认 20MB，检查网关 `proxy-body-size` 是否与
  后端对齐（Chart `ingress.proxyBodySize` 默认 20m）。

## 高可用与容量

- **上传持久化**：Chart `storage.enabled=true`（默认）创建上传 PVC，挂到后端
  `/data/uploads`；多副本必须使用 **ReadWriteMany（RWX）** 存储类
  （`storage.className`），否则 Pod 间文件不可见、Pod 重建即丢。
- **Redis 主从哨兵**：设置 `config.redisSentinelMaster` + `config.redisSentinelNodes`
  后后端自动切换哨兵拓扑（留空则单实例）。**master 与 nodes 必须成对设置**，只设
  其一因占位符缺失会启动失败。主节点故障时哨兵自动提升从节点，应用无需重启。
- **副本打散**：backend / ai 等带 `topologySpreadConstraints`（软约束），单节点故障
  只损失单副本。
- **优雅停机**：滚动更新时 readiness 先转 Down、旧副本再关闭（Spring graceful
  shutdown + 关闭超时），`storage.terminationGracePeriodSeconds`（默认 60s）须大于
  应用关闭窗口，否则在途请求被截断。
- **水平扩容（HPA）**：Chart 为 backend/ai/frontend/website 默认声明 HPA
  （CPU 平均利用率 70%，min/max 以 `values.yaml`/模板为准，依赖 metrics-server）。
  扩 AI 副本会把 LLM 并发翻倍——先核对 Provider RPM/TPM 配额再扩。
- **数据库/存储扩容**：MySQL 升配或读写分离（云 RDS 优先）；Redis 启用哨兵/集群；
  上传卷按存储类能力扩容 PVC 或迁移更大 RWX 卷。

## 密钥轮换流程

密钥一律不留默认值，由 Helm Secret 注入（`kubectl edit secret` 或 ESO 同步）。轮换
按序执行、避免全站中断：

1. **DB 密码**：云 RDS 改密 → 更新 Secret（ESO 同步或 `kubectl edit secret`）→
   `kubectl -n admin-scaffold rollout restart deployment/<release>-backend`。
2. **JWT_SECRET**：生成新密钥 → 更新 Secret → 滚动重启 backend。存量 access token
   与 refresh 令牌全部失效，用户需重新登录（属预期，建议低峰执行）。
3. **TOTP_ENCRYPTION_KEY**：生成新密钥 → 更新 Secret → 滚动重启 backend。存量 TOTP
   密钥按旧 key 加密，换 key 后已绑定用户的动态码失效需重新绑定——提前通知并在
   低峰窗口执行。
4. **AI 鉴权密钥（ai-service `AUTH_TOKEN` = 后端 AI 服务配置 `apiKey`）与
   `MCP_AUTH_TOKEN`**：更新 Secret 后**同时滚动重启 backend 与 ai-service**（两处
   必须一致，否则 401 / 回调签名失败）。
5. 轮换后验证：`scripts/smoke.ps1` 全绿 + 一次真实登录 + 一次真实 AI 任务。

## 版本升级与回滚

- **发布形态**：生产只走 Helm Chart（`k8s/helm/admin-scaffold`）或 Argo CD
  （`gitops/argocd`），禁止直接 `kubectl apply` 零散资源（避免清单漂移）。镜像按
  sha 固定（生产 values，如 `gitops/argocd/values-prod.yaml`）。
- **升级**：`helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold
  --namespace admin-scaffold --set images.backend=<new-sha> ...`；含大版本 Flyway
  迁移时：先备份数据库 → 单副本试跑迁移 → 验证后全量滚动。
- **回滚**：`helm rollback admin-scaffold <revision>`。回滚前必须确认数据库迁移
  兼容——Flyway 前向迁移不可逆，若本次含破坏性迁移，需先恢复数据库备份再回滚应用。

## 生产发布检查项

- [ ] 部署只走 Helm/Argo CD，镜像 sha 固定
- [ ] Secret 已注入：`JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、DB 密码、AI `AUTH_TOKEN`
      与后端 AI 配置 `apiKey` 一致、`MCP_AUTH_TOKEN`
- [ ] 上传卷存储类为 RWX（`--set storage.className=...`）；Redis 单点按需启用哨兵
      （master + nodes 成对）
- [ ] HTTPS 与 WAF（`ingress.tls.enabled=true`、`ingress.forceHttps=true`），
      `proxy-body-size` 与上传上限对齐
- [ ] 监控与告警就绪（Prometheus scrape 路径正确、exporter job 已启用、告警路由到人）
- [ ] 已执行 `scripts/backup-drill.ps1` 备份演练并记录恢复耗时
- [ ] 已验证租户隔离、数据权限与一次真实 AI 任务闭环
- [ ] 上线窗口低峰、有回滚预案（含数据库回退方式）

相关文档：`docs/deploy/README.md`、`docs/deploy/cluster-go-nogo.md`（集群交付与
Go/No-Go 检查）、`docs/database/README.md`（数据库与迁移规范）、`docs/api/README.md`
（接口约定）、`docs/ai-service.md`（AI 服务内部机制）。
