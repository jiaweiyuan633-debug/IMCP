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

- Flyway 自动迁移，当前版本 V1-V25。
- 禁止直接修改已执行的迁移脚本；变更必须新增 V 系列脚本。
- 定期执行 `scripts/backup-drill.ps1` 验证备份可恢复。

## 日志与监控

- 后端指标：`/actuator/prometheus`
- 健康检查：`/actuator/health`
- AI 指标：`/api/v1/metrics`
- 启用可观测性：

```bash
cd docker
docker compose -f docker-compose.yml -f observability.yml up -d
```

- Grafana 默认地址：`http://localhost:3000`

## 常见问题

### 登录提示 401

确认 `JWT_SECRET` 一致，并检查 Redis Token 是否被清理。

### AI 任务一直 PENDING

确认 AI 服务可访问，`CALLBACK_BASE_URL` 能被 Python 服务回调，检查 `ai-task` 日志。

### 通知收不到

检查 SSE Ticket 是否过期，多实例场景确认 Redis 通道 `notice:sse` 正常。

### 文件访问 403

文件访问需要短期签名 Token，重新上传或通过文件管理页获取 Token。

## 生产发布检查项

- 配置 `JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、数据库密码、AI 回调 Token
- 开启 HTTPS 与 WAF
- 配置 Prometheus + Grafana 告警
- 执行备份演练
- 验证租户隔离和数据权限
