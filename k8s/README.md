# Kubernetes 部署

本目录提供两种部署方式：原生清单和 Helm Chart。

## 原生清单

- `configmap.yaml`：后端与 AI 服务公共环境配置（不含任何密钥）
- `manifests.yaml`：backend / ai-service / frontend Deployment、Service、Ingress
- 密钥由独立 Secret `admin-secret` 提供，**仓库内不提交任何明文密钥**

使用步骤：

- 先部署 MySQL/Redis（可选用云托管或自建）。
- 替换镜像地址、ConfigMap 中的数据库地址和 Ingress Host。
- 创建密钥 Secret（必填，缺省则 backend 因 `SPRING_PROFILES_ACTIVE=prod` 启动失败，fail-fast）：

```bash
# JWT_SECRET 建议 ≥32 位随机串；DB_PASSWORD / TOTP_ENCRYPTION_KEY 为强口令
kubectl create secret generic admin-secret \
  --namespace admin-scaffold \
  --from-literal=DB_PASSWORD='<强口令>' \
  --from-literal=JWT_SECRET='<≥32 位随机串>' \
  --from-literal=TOTP_ENCRYPTION_KEY='<随机串>' \
  --from-literal=AUTH_TOKEN='<随机串，与后端 AiServiceConfig.apiKey 保持一致>' \
  --from-literal=MCP_AUTH_TOKEN='<随机串，MCP Server 端点鉴权令牌>'
```

- 执行：

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

## Helm Chart

Chart 位于 `k8s/helm/admin-scaffold`，提供 ConfigMap、Secret、Deployment、Service、HPA、Ingress 与官网服务。

**密钥必须通过 `--set secret.*` 显式注入**：values.yaml 中三个密钥默认留空，未注入或留空时模板渲染直接失败，杜绝明文默认密钥上生产：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace \
  --set images.backend=registry.example.com/admin-backend:1.0.0 \
  --set images.ai=registry.example.com/ai-service:1.0.0 \
  --set images.frontend=registry.example.com/admin-frontend:1.0.0 \
  --set config.dbHost=mysql \
  --set secret.dbPassword='<强口令>' \
  --set secret.jwtSecret='<≥32 位随机串>' \
  --set secret.totpEncryptionKey='<随机串>' \
  --set secret.aiAuthToken='<随机串，与后端 AiServiceConfig.apiKey 保持一致>' \
  --set secret.mcpAuthToken='<随机串，MCP Server 端点鉴权令牌>' \
  --set ingress.host=admin.example.com
```

主要 `values.yaml` 参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `images.*` | `*-latest` | 三个服务镜像 |
| `replicas.*` | `2` | 副本数 |
| `images.website` | `website:latest` | 官网镜像 |
| `config.*` | 本机环境 | DB/Redis/回调/AI/前端 URL 与 CORS |
| `secret.dbPassword` | 空（必填） | 数据库密码 |
| `secret.jwtSecret` | 空（必填） | JWT 密钥（≥32 位） |
| `secret.totpEncryptionKey` | 空（必填） | TOTP 加密密钥 |
| `secret.aiAuthToken` | 空（必填） | AI 服务鉴权密钥（= 后端 AiServiceConfig.apiKey） |
| `secret.mcpAuthToken` | 空（必填） | MCP Server 端点鉴权令牌 |
| `ingress.host` | `admin.example.com` | Ingress 域名 |

生产建议：

- 密钥经 `helm --set` 或外部密钥管理（Vault / External Secrets Operator）注入，避免明文出现在 Values
- 使用云数据库/托管 Redis 或独立部署高可用存储
- 开启 HTTPS Ingress 和外部负载均衡
- 日志采集到 Loki/ELK，指标接入 Prometheus + Grafana
- 定期执行 `scripts/backup.ps1` 并演练恢复

官网 `website/` 如需 K8s 部署，可参照 `frontend` 的 Deployment/Service/Ingress 模板扩展。

后端数据库迁移由 Flyway V1-V29 自动执行，发布前确认迁移脚本与镜像版本一致。
