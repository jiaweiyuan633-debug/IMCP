# 运维手册

## 常用命令

```powershell
scripts/start-dev.ps1
scripts/stop-dev.ps1
scripts/smoke.ps1
scripts/backup.ps1
scripts/restore.ps1
scripts/backup-drill.ps1
scripts/load-test.ps1
scripts/load-test-multi.ps1
```

## 后端测试

- 单元测试（surefire）：`cd backend && mvn test`，约 300 用例，无外部依赖。
- 全量验证（failsafe + jacoco）：`cd backend && mvn verify`，执行 Testcontainers 集成测试（`*IT`，MySQL + Redis 容器 + Flyway 迁移）并通过覆盖率门槛（LINE≥40% / BRANCH≥36% / METHOD≥37%，随 R4-1.36 门禁上调同步）。
- 集成测试基类 `AbstractIntegrationTest` 使用 JVM 级单例 MySQL + Redis 容器（整个测试 JVM 只启动一次，规避 Windows Docker 反复建/删容器时端口映射偶发 `Connection refused`），8 个 `*IT` 类共享同一 Spring 上下文，全量约 2 分钟；无有效 Docker 环境时自动跳过 IT，不阻塞构建。
- Windows + Docker Desktop 29.x 注意：named pipe 实现 bootstrap redirect 且要求 Docker API ≥ v1.44，而 docker-java 默认协商 v1.32 且不跟随重定向，导致 IT 全部 Skipped（`BadRequest 400`）。修复：在用户目录 `~/.docker-java.properties` 写入 `api.version=1.44`（docker-java 全局配置，仅本机生效，不影响 CI）。

## 数据库

- Flyway 自动迁移，当前版本 V1-V62。
- 禁止直接修改已执行的迁移脚本；变更必须新增 V 系列脚本。
- 定期执行 `scripts/backup-drill.ps1` 验证备份可恢复。

## 备份与恢复

- `scripts/backup.ps1` 输出到 `backups/<db>_<时间戳>/` 目录：MySQL SQL 全量（批次6·R4-1.52 起口令经 `MYSQL_PWD` 注入、`--result-file` 直落盘，避免命令行明文与管道 BOM）+ Redis RDB 快照（`redis-cli --rdb`，密码经 `REDISCLI_AUTH` 注入）+ MinIO 桶镜像（`mc mirror`，提供 `-MinioEndpoint/-MinioAccessKey/-MinioSecretKey/-MinioBucket` 后启用）。
- `scripts/restore.ps1` 恢复 MySQL 后默认执行校验（表数量 + 关键表 `sys_user` 抽查），`-SkipVerify` 可关闭；Redis RDB 恢复需停机替换 dump.rdb（脚本给出手动步骤，不自动执行）；MinIO 恢复通过 `mc mirror` 反向回写。
- Redis 恢复注意：RDB 恢复会覆盖当前实例全量数据，执行前务必确认目标实例与备份来源一致。
- 备份文件含业务数据与文件对象，归档与传输需按敏感数据处理。

### 定时与异地（批次6·R4-1.52，生产必配）

- **定时执行**：Windows 计划任务 / Linux cron 每日执行 `backup.ps1`。示例（Linux cron，每日 02:00）：
  ```bash
  0 2 * * * /opt/admin-scaffold/scripts/backup.ps1 -DbHost <db> -DbUser <user> -DbPassword "$(cat /run/secrets/db_pwd)" -OutputDir /backups -SkipRedis 2>> /var/log/backup.log
  ```
- **异地/对象存储**：备份目录定期同步到异地对象存储（MinIO/S3），`mc mirror /backups s3/bucket/admin-scaffold-backups`；或直接把 `OutputDir` 指向挂载的对象存储目录。同机同盘备份在磁盘故障时与主库同毁。
- **时间点恢复（PITR）**：`mysqldump --single-transaction` 仅提供"最近一次全量"恢复点。要求 PITR 时：① 开启 MySQL `binlog`（`log_bin=ON`），全量备份后定期 `mysqlbinlog --start-datetime=... binlog.* > incr.sql` 归档增量；② 或使用云 RDS 自动备份（自带 PITR）。恢复 = 最近全量 + 增量重放至目标时刻。
- **RPO/RTO 目标**：建议全量每日（RPO≤24h）+ binlog 增量（RPO≤分钟级）；RTO 以最近一次 backup-drill 实测为准。
- **演练**：每季度执行 `backup-drill.ps1` 在独立环境恢复验证，记录恢复耗时。

## 日志与监控

- 后端指标：`/actuator/prometheus`
- 健康检查：`/actuator/health`（K8s 探针：readiness 用 `/actuator/health/readiness`、liveness 用 `/actuator/health/liveness`）
- AI 指标：`/metrics`（根路径；批次5·R4-1.51 修正，此前文档误述为 `/api/v1/metrics`）
- AI 探针：liveness `/livez`、readiness `/readyz`（批次3·R4-1.49，原共用 `/health` 依赖 Redis 会误杀）
- Prometheus/Grafana 按部署环境配置，Grafana 默认地址：`http://localhost:3000`

## 常见问题

### 登录提示 401

确认 `JWT_SECRET` 一致，并检查 Redis Token 是否被清理。

### AI 任务一直 PENDING

确认 AI 服务可访问，`CALLBACK_BASE_URL` 能被 Python 服务回调，检查 `ai-task` 日志。

### 通知收不到

检查 SSE Ticket 是否过期，多实例场景确认 Redis 通道 `notice:sse` 正常。

### 文件访问 403

文件访问需要短期签名 Token，重新上传或通过文件管理页获取 Token。

## 高可用（P2-15）

- **上传持久化**：Helm 默认创建上传 PVC（`storage.enabled=true`）挂到后端 `/data/uploads`，`UPLOAD_PATH` 指向挂载点。多副本必须使用 ReadWriteMany（RWX）存储类，否则 Pod 间文件不可见、Pod 重建即丢。
- **Redis 主从哨兵**：设置 `config.redisSentinelMaster` + `config.redisSentinelNodes` 后后端自动切换哨兵拓扑（`application-prod.yml` `on-property: REDIS_SENTINEL_MASTER` 条件激活）；留空则单实例。切换时**必须成对设置 master 与 nodes**，只设其一会因 placeholder 缺失而启动失败。主节点故障时哨兵自动提升从节点，应用无需重启。
- **多副本打散**：backend/ai 带 `topologySpreadConstraints`（软约束），故障节点只损失单副本。
- **优雅停机**：prod 关闭超时 30s，滚动更新时 readiness 先转 Down 再关闭，避免在途请求中断。若探针窗口不足，检查 `storage.terminationGracePeriodSeconds`（默认 60s）是否大于关闭超时。

## 生产发布检查项

- 部署只走 Helm Chart（`k8s/helm/admin-scaffold`）或 ArgoCD，禁止直接 `kubectl apply` 零散资源，避免清单漂移
- 配置 `JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、数据库密码、AI 回调 Token
- 确认上传卷存储类为 RWX（`--set storage.className=...`），Redis 单点则按需启用哨兵
- 开启 HTTPS 与 WAF（`--set ingress.tls.enabled=true --set ingress.forceHttps=true`）
- 配置 Prometheus + Grafana 告警
- 执行备份演练
- 验证租户隔离和数据权限

## 生产运维（批次8·R4-1.54）

### 告警处置对照表

| 告警 | 触发 | 影响 | 排查步骤 |
| --- | --- | --- | --- |
| AdminBackendDown / AIServiceDown | `up==0` 2 分钟 | 服务不可用 | `kubectl get pods -n admin-scaffold`；`kubectl logs -l app=<release>-backend --tail=200`；检查资源/探针/网络策略 |
| BackendHighErrorRate | 5xx >5% 5 分钟 | 接口故障 | Grafana 按 `application/instance/uri` 定位；检查日志异常堆栈、下游依赖 |
| BackendHighLatency | P95 >1s | 体验劣化 | 定位慢接口；检查慢 SQL（`sys_sql_log`）、外部调用、GC |
| BackendJvmHeapHighUsage | 堆 >90% | OOM 风险 | dump 分析（`jmap -dump`）；检查泄漏；必要时扩容 |
| BackendThreadsBusy | Tomcat 忙线程 >90% | 请求排队 | 检查慢请求/线程泄漏；扩容副本 |
| RedisDown / MySqlDown | exporter 不可达 | 全站故障 | 检查对应 Pod/云实例；注意**需启用 prometheus.yml 对应 exporter job** |
| OutboxDeadLetterAccumulating | 发件箱投递失败 | 消息/回调缺失 | 查 `sys_outbox` 表；检查回调端点/网络 |
| AiDeadLetterQueueGrowth | AI 重试耗尽 >10/h | AI 任务失败 | 查死信（AI `/api/v1/tasks/dead`）；检查 LLM Provider 配置/配额 |
| PersistentVolumeAlmostFull | PVC >85% | 写入失败 | 清理或扩容 PVC |

### 水平扩容

- 应用：`helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold --set replicas.backend=4 ...`
  （HPA 默认 cpu 70% 自动扩 2~6 副本，依赖 metrics-server）。扩 AI 副本会把 LLM 并发翻倍，
  先核对 Provider RPM/TPM 配额。
- 数据库：MySQL 升配/读写分离（云 RDS 优先）；Redis 启用哨兵/集群（`config.redisSentinel*`）。
- 上传卷：PVC 扩容（按存储类能力）或迁移更大 RWX 卷。

### 密钥轮换流程（分批，避免全站中断）

1. **DB 密码**：云 RDS 改密 → 更新 Secret（ESO 同步或 `kubectl edit secret`）→
   `kubectl -n admin-scaffold rollout restart deployment/admin-scaffold-backend`。
2. **JWT_SECRET**：生成新密钥 → 更新 Secret → 滚动重启 backend（存量 access token 失效，
   用户需重新登录；刷新令牌同样失效，属预期）。
3. **TOTP_ENCRYPTION_KEY**：生成新密钥 → 更新 Secret → 滚动重启 backend。注意：存量
   TOTP 密钥按旧 key 加密，换 key 后所有已绑定 TOTP 用户的动态码失效，需重新绑定——
   建议在低峰窗口执行并提前通知，或先轮换再让用户重绑。
4. **AI AUTH_TOKEN / MCP_AUTH_TOKEN**：更新 Secret → 同时滚动重启 backend 与 ai-service
   （两处必须一致，否则 401/回调签名失败）。
5. 轮换后验证：`scripts/smoke.ps1` 全绿 + 一次真实登录 + 一次 AI 任务。

### 版本升级与回滚

- 升级：`helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold --namespace admin-scaffold
  --set images.backend=<new-sha> ...`（生产 values 固定 sha 镜像，见 `gitops/argocd/values-prod.yaml`）。
- 大版本 Flyway 迁移：先备份数据库 → 单副本试跑迁移 → 验证后全量滚动。
- 回滚：`helm rollback admin-scaffold <revision>`（回滚前确认数据库迁移已兼容——
  Flyway 前向迁移不可逆，若本次含破坏性迁移需先恢复数据库备份再回滚应用）。
