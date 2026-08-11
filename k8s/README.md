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
| `config.redisSentinelMaster` | 空（可选） | 设置后后端切换 Redis 主从哨兵拓扑（须与 `redisSentinelNodes` 成对） |
| `config.redisSentinelNodes` | 空（可选） | 逗号分隔哨兵节点，如 `host1:26379,host2:26379` |
| `config.redisSentinelPassword` | 空（可选） | 哨兵节点认证口令 |
| `storage.enabled` | `true` | 是否创建上传 PVC 并挂载（关掉则后端用本地临时盘） |
| `storage.className` | 空（默认存储类） | PVC 存储类名；多副本需 ReadWriteMany（RWX） |
| `storage.accessMode` | `ReadWriteMany` | 上传卷访问模式，多副本共享必须 RWX |
| `storage.size` | `10Gi` | 上传卷容量 |
| `storage.uploadPath` | `/data/uploads` | 后端上传目录（挂载点），同时是 `/uploads` 静态资源根 |
| `storage.terminationGracePeriodSeconds` | `60` | Pod 优雅停机宽限期，须 > 后端关闭超时（30s） |
| `secret.dbPassword` | 空（必填） | 数据库密码 |
| `secret.jwtSecret` | 空（必填） | JWT 密钥（≥32 位） |
| `secret.totpEncryptionKey` | 空（必填） | TOTP 加密密钥 |
| `secret.aiAuthToken` | 空（必填） | AI 服务鉴权密钥（= 后端 AiServiceConfig.apiKey） |
| `secret.mcpAuthToken` | 空（必填） | MCP Server 端点鉴权令牌 |
| `ingress.host` | `admin.example.com` | Ingress 域名 |

## 高可用（P2-15）

- **上传持久化**：本地存储多副本必须共享同一上传卷。Chart 默认创建 `admin-scaffold-upload` PVC（`storage.enabled=true`）并挂到后端 `/data/uploads`，`UPLOAD_PATH` 指向挂载点，Pod 重建/滚动更新不丢文件。**多副本要求 ReadWriteMany（RWX）存储类**（`storage.className` 指定），单副本可改 `ReadWriteOnce`。
- **Redis 主从哨兵**：设置 `config.redisSentinelMaster` + `config.redisSentinelNodes` 后，后端自动切换为主从哨兵拓扑（`application-prod.yml` 以 `spring.config.activate.on-property: REDIS_SENTINEL_MASTER` 条件激活，连接工厂优先 sentinel 配置）；留空则走单实例 `redisHost/redisPort`。空 master 不注入 env，避免空字符串误激活哨兵段。哨兵连接为惰性，主节点故障自动切换。
- **多副本打散**：backend/ai 加 `topologySpreadConstraints`（`kubernetes.io/hostname`，`ScheduleAnyway` 软约束），避免单节点故障拖垮全部实例。
- **优雅停机**：prod 开启 `server.shutdown: graceful`（30s 关闭超时），滚动更新时 readiness 先转 Down 摘流量再关闭，不中断在途请求；K8s `terminationGracePeriodSeconds: 60` 兜底。

生产建议：

- 密钥经 `helm --set` 或外部密钥管理（Vault / External Secrets Operator / Sealed Secrets）注入，避免明文出现在 Values
- 使用云数据库/托管 Redis 或独立部署高可用存储（自建 Redis 建议启用哨兵并设置 `config.redisSentinel*`）
- 上传卷使用 RWX 存储类（如 EFS / NFS / CephFS）。若改用对象存储，仓库已提供 `MinioFileStorage`（`STORAGE_TYPE=minio`），但需自行向容器注入 `app.storage.minio.*`（endpoint/access-key/secret-key/bucket，见 `application.yml`）；Chart 默认只覆盖本地存储路径
- 开启 HTTPS Ingress 和外部负载均衡
- 日志采集到 Loki/ELK，指标接入 Prometheus + Grafana（采集与告警配置见 `k8s/monitoring/`）
- 定期执行 `scripts/backup.ps1` 并演练恢复

后端数据库迁移由 Flyway V1-V52 自动执行，发布前确认迁移脚本与镜像版本一致。
