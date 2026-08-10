# Kubernetes 部署

本目录只保留一种部署方式：Helm Chart（`helm/admin-scaffold`），作为 **Kubernetes 部署的唯一来源**。原生清单（`manifests.yaml` / `configmap.yaml`）已移除，避免两份声明漂移。

> 部署原则：`helm upgrade`（或 ArgoCD 指向 Helm chart）是唯一的声明来源。禁止直接 `kubectl apply` 零散资源，否则会与 Chart 漂移并被 `prune/selfHeal` 回滚。

## Helm Chart

Chart 位于 `k8s/helm/admin-scaffold`，提供 backend / ai-service / frontend / website 的 Deployment、Service、HPA、PDB、Ingress，以及 ConfigMap、Secret 与 NetworkPolicy。

**密钥必须通过 `--set secret.*` 显式注入**：values.yaml 中 5 个密钥默认留空，未注入或留空时模板顶部 `fail` 校验直接终止渲染，杜绝明文默认密钥上生产：

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

先决条件：先部署 MySQL / Redis（可选用云托管或自建）。

主要 `values.yaml` 参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `images.*` | `*-latest` | 四个服务镜像（backend/ai/frontend/website） |
| `replicas.*` | `2` | 各服务副本数 |
| `config.*` | 本机环境 | DB/Redis/回调/AI/前端 URL 与 CORS |
| `secret.dbPassword` | 空（必填） | 数据库密码 |
| `secret.jwtSecret` | 空（必填） | JWT 密钥（≥32 位） |
| `secret.totpEncryptionKey` | 空（必填） | TOTP 加密密钥 |
| `secret.aiAuthToken` | 空（必填） | AI 服务鉴权密钥（= 后端 AiServiceConfig.apiKey） |
| `secret.mcpAuthToken` | 空（必填） | MCP Server 端点鉴权令牌 |
| `ingress.host` | `admin.example.com` | Ingress 域名 |

生产建议：

- 密钥经 `helm --set` 或外部密钥管理（Vault / External Secrets Operator / Sealed Secrets）注入，避免明文出现在 Values
- 使用云数据库/托管 Redis 或独立部署高可用存储
- 开启 HTTPS Ingress 和外部负载均衡
- 日志采集到 Loki/ELK，指标接入 Prometheus + Grafana（采集与告警配置见 `k8s/monitoring/`）
- 定期执行 `scripts/backup.ps1` 并演练恢复

后端数据库迁移由 Flyway V1-V41 自动执行，发布前确认迁移脚本与镜像版本一致。
