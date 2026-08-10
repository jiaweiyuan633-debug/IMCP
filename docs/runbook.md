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

## 数据库

- Flyway 自动迁移，当前版本 V1-V41。
- 禁止直接修改已执行的迁移脚本；变更必须新增 V 系列脚本。
- 定期执行 `scripts/backup-drill.ps1` 验证备份可恢复。

## 备份与恢复

- `scripts/backup.ps1` 输出到 `backups/<db>_<时间戳>/` 目录：MySQL SQL 全量 + Redis RDB 快照（`redis-cli --rdb`，密码经 `REDISCLI_AUTH` 注入）+ MinIO 桶镜像（`mc mirror`，提供 `-MinioEndpoint/-MinioAccessKey/-MinioSecretKey/-MinioBucket` 后启用）。
- `scripts/restore.ps1` 恢复 MySQL 后默认执行校验（表数量 + 关键表 `sys_user` 抽查），`-SkipVerify` 可关闭；Redis RDB 恢复需停机替换 dump.rdb（脚本给出手动步骤，不自动执行）；MinIO 恢复通过 `mc mirror` 反向回写。
- Redis 恢复注意：RDB 恢复会覆盖当前实例全量数据，执行前务必确认目标实例与备份来源一致。
- 备份文件含业务数据与文件对象，归档与传输需按敏感数据处理。

## 日志与监控

- 后端指标：`/actuator/prometheus`
- 健康检查：`/actuator/health`
- AI 指标：`/api/v1/metrics`
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
- 开启 HTTPS 与 WAF
- 配置 Prometheus + Grafana 告警
- 执行备份演练
- 验证租户隔离和数据权限
