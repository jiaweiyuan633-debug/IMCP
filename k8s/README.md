# Kubernetes 部署

本目录提供两种部署方式：原生清单和 Helm Chart。

## 原生清单

- `configmap.yaml`：后端与 AI 服务公共环境配置
- `manifests.yaml`：backend / ai-service / frontend Deployment、Service、Ingress

使用步骤：

1. 先部署 MySQL/Redis（可选用云托管或自建）。
2. 替换镜像地址、ConfigMap 中的数据库地址/密码和 Ingress Host。
3. 执行：

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

## Helm Chart

Chart 位于 `k8s/helm/admin-scaffold`，提供 ConfigMap、Secret、Deployment、Service、HPA、Ingress 与官网服务：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace \
  --set images.backend=registry.example.com/admin-backend:1.0.0 \
  --set images.ai=registry.example.com/ai-service:1.0.0 \
  --set images.frontend=registry.example.com/admin-frontend:1.0.0 \
  --set config.dbHost=mysql \
  --set secret.dbPassword=root123456 \
  --set secret.jwtSecret=change-me \
  --set ingress.host=admin.example.com
```

主要 `values.yaml` 参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `images.*` | `*-latest` | 三个服务镜像 |
| `replicas.*` | `2` | 副本数 |
| `images.website` | `website:latest` | 官网镜像 |
| `config.*` | 本机环境 | DB/Redis/回调/AI 地址 |
| `secret.*` | 示例值 | 数据库密码、JWT Secret |
| `ingress.host` | `admin.example.com` | Ingress 域名 |

生产建议：

- 密码放入 Secret，避免明文出现在 Values
- 使用云数据库/托管 Redis 或独立部署高可用存储
- 开启 HTTPS Ingress 和外部负载均衡
- 日志采集到 Loki/ELK，指标接入 Prometheus + Grafana
- 定期执行 `scripts/backup.ps1` 并演练恢复

官网 `website/` 如需 K8s 部署，可参照 `frontend` 的 Deployment/Service/Ingress 模板扩展。

后端数据库迁移由 Flyway V1-V29 自动执行，发布前确认迁移脚本与镜像版本一致。

