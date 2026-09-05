# Kubernetes 部署（Helm）

本目录只保留一种部署方式：Helm Chart（`helm/admin-scaffold`），是 **Kubernetes 部署的唯一声明来源**。原生清单（`manifests.yaml` / `configmap.yaml`）已移除，避免两份声明漂移。

> 部署原则：用 `helm upgrade` 安装/升级（或由 ArgoCD 指向该 Chart 声明式同步），禁止直接 `kubectl apply` 零散资源——手工改动会与 Chart 漂移并被 `prune/selfHeal` 回滚。

## 1. Chart 范围

Chart 位于 `k8s/helm/admin-scaffold`（`Chart.yaml` / `values.yaml` / `templates/all.yaml` / `templates/networkpolicy.yaml`），为 backend / ai-service / frontend / website 渲染：

- Deployment + Service、HPA、PDB、Ingress、ConfigMap、Secret、上传 PVC、NetworkPolicy（default-deny + 每服务入站规则）。
- backend 强制 `SPRING_PROFILES_ACTIVE=prod`，配置/密钥经 `envFrom`（`{release}-config` ConfigMap + `{release}-secret` Secret，或 `secret.existingSecret` 指定的外部 Secret）注入。
- backend/ai/frontend/website 各默认 2 副本，backend/ai 带 topologySpreadConstraints 打散。

## 2. 前置条件

### 2.1 集群组件（缺失的后果各不同）

| 组件 | 用途 | 缺失影响 | 安装示例 |
| --- | --- | --- | --- |
| metrics-server | 提供 `metrics.k8s.io`，HPA（CPU 指标）依赖 | HPA 指标未知、静默不伸缩 | `kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml` |
| ingress-controller | 承载 Ingress 路由（/api、/files、/uploads、/ws → backend） | 外部流量无法按路径到达服务 | `helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace` |
| RWX 存储类 | 多副本上传卷（backend 共享同一 `UPLOAD_PATH`）；如 EFS/NFS/CephFS 动态供给 | 多副本后端挂载/写入上传卷失败 | 按云厂商创建 accessModes=ReadWriteMany 的 StorageClass，Chart `--set storage.className=<类名>` |
| cert-manager | Ingress TLS 自动签发（`ingress.tls.enabled=true` 时必配） | 证书无法签发，TLS 段长期 Pending | `helm upgrade --install cert-manager jetstack/cert-manager -n cert-manager --create-namespace --set installCRDs=true` |

校验要点：

- metrics-server：`kubectl top nodes` 有非空输出才算真正服务（仅 Deployment Ready 不够）。
- ingress-controller：Deployment Ready 且 Service 有 EXTERNAL-IP、存在被引用的 IngressClass。
- cert-manager：三个 Deployment Ready，且至少一个 ClusterIssuer/Issuer `READY=True`，名称与 `ingress.tls.clusterIssuer` 一致。

完整验收命令见 [`../docs/deploy/cluster-go-nogo.md`](../docs/deploy/cluster-go-nogo.md)。

### 2.2 基础设施（业务依赖）

先部署 **MySQL / Redis**（云托管或自建均可），再把 `config.dbHost/dbPort/dbName/dbUser`、`config.redisHost/redisPort/redisUrl` 指向实际可达地址。生产建议：

- 云数据库/托管 Redis；自建 Redis 启用主从哨兵并设置 `config.redisSentinelMaster/Nodes/Password`。
- 密钥经外部密钥管理（Vault / External Secrets Operator / Sealed Secrets）注入，避免明文进 Values，见 [`../gitops/argocd/eso-example/README.md`](../gitops/argocd/eso-example/README.md)。
- 监控栈（Prometheus/Grafana）部署说明见 [`monitoring/grafana/README.md`](monitoring/grafana/README.md)。

## 3. 安装

**密钥必须显式注入**：`values.yaml` 中 5 个密钥默认留空，未注入时 `templates/all.yaml` 顶部 `fail` 校验直接终止渲染（fail-fast），杜绝明文默认密钥上生产。安装示例：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace \
  --set images.backend=registry.example.com/admin-backend:1.0.0 \
  --set images.ai=registry.example.com/ai-service:1.0.0 \
  --set images.frontend=registry.example.com/admin-frontend:1.0.0 \
  --set images.website=registry.example.com/website:1.0.0 \
  --set config.dbHost=<MySQL 地址> \
  --set config.redisHost=<Redis 地址> \
  --set secret.dbPassword='<强口令>' \
  --set secret.jwtSecret='<≥32 位随机串>' \
  --set secret.totpEncryptionKey='<随机串>' \
  --set secret.aiAuthToken='<随机串，与后端 AiServiceConfig.apiKey 保持一致>' \
  --set secret.mcpAuthToken='<随机串，MCP Server 端点鉴权令牌>' \
  --set ingress.host=admin.example.com
```

生产覆盖通常写成一个 override values 文件再 `-f` 引用，示例见 [`../gitops/argocd/values-prod.yaml`](../gitops/argocd/values-prod.yaml)（该文件 `secret.*` 留空，密钥由部署时注入）。

### 3.1 密钥注入（fail-fast 与 externalSecret）

- 逐项 `--set secret.*=...`：全部 5 项非空才会渲染出 `{release}-secret`（Opaque，键名 `DB_PASSWORD/JWT_SECRET/TOTP_ENCRYPTION_KEY/AUTH_TOKEN/MCP_AUTH_TOKEN`）。
- `secret.existingSecret=<Secret 名>`：跳过空值校验与内部 Secret 创建，`envFrom` 直接引用该外部 Secret（ESO/手工创建均可）——GitOps 场景推荐，无需在 values 里填任何明文。
- 渲染自检：`helm template ./k8s/helm/admin-scaffold --namespace admin-scaffold`（不带 secret 应失败、带 `--set secret.existingSecret=...` 应成功）。

## 4. 升级 / 回滚 / 卸载

```bash
# 升级：同一 release 名重新 upgrade（-f 生产 override + 注入新密钥或沿用 existingSecret）
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold -n admin-scaffold -f gitops/argocd/values-prod.yaml

# 升级前本地渲染校验（不连集群）
helm template admin-scaffold ./k8s/helm/admin-scaffold -n admin-scaffold -f <override>

# 回滚到上一个版本
helm rollback admin-scaffold <REVISION> -n admin-scaffold

# 卸载（会删除 Chart 创建的 PVC 等对象；底层 PV/数据按存储类回收策略处理，需先确认）
helm uninstall admin-scaffold -n admin-scaffold
```

GitOps 方式：ArgoCD Application 指向本 Chart（[`../gitops/argocd/application.yaml`](../gitops/argocd/application.yaml)，auto-sync + prune + selfHeal）。密钥未注入时 ArgoCD 同步会失败——这是 fail-fast 的预期保护，注入后恢复 `Synced`。

## 5. values 关键项

> 下列默认值为本文件核对 `k8s/helm/admin-scaffold/values.yaml` 时的取值；实际以该文件当前内容为准（镜像 tag、TLS 等默认可能随发布调整）。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `images.backend/ai/frontend/website` | `*-latest` | 各服务镜像；生产覆盖为固定 tag 的私有仓库镜像（如 `registry.example.com/...:1.0.0`） |
| `replicas.*` | `2` | 各服务副本数（HPA min 同此值） |
| `resources.*` / `securityContext.*` | 见 `values.yaml` | 每服务 request/limit 与 `runAsNonRoot` 等安全上下文 |
| `config.dbHost/dbPort/dbName/dbUser` | `mysql/3306/admin_scaffold/root` | MySQL 连接；生产覆盖为实际可达地址（云 RDS 等） |
| `config.redisHost/redisPort/redisUrl` | `redis/6379/redis://redis:6379/0` | Redis 单实例连接；生产覆盖为实际地址 |
| `config.callbackBaseUrl` / `config.aiBaseUrl` / `config.frontendUrl` | 空 → 按 release 名推导 | 留空时模板推导为 `http://{release}-backend:8080` / `http://{release}-ai:8000` / `http://{release}-frontend:8080`，与 Chart 内 Service 命名一致；Service 名不同或需集群外可达地址时显式覆盖。另：`CALLBACK_ALLOWED_ORIGINS` 由模板从 `callbackBaseUrl` 推导，无需手工维护 |
| `config.corsAllowedOriginPatterns` | `https://admin.example.com` | 逗号分隔前端域名（可含 `*`），注入 `CORS_ALLOWED_ORIGIN_PATTERNS`。占位域名必须覆盖为实际前端域名；留空时后端 prod 配置层为空串即拒绝全部跨域来源，浏览器 API 请求返回 `403 Invalid CORS request` |
| `config.redisSentinelMaster/Nodes/Password` | 空 | 主从哨兵拓扑，`master` 非空才注入环境变量（避免空值误激活哨兵段）；`nodes` 为逗号分隔哨兵地址，与 `master` 成对设置 |
| `config.zipkinEndpoint` | 空 | Zipkin 上报端点；留空注入 `MANAGEMENT_TRACING_ENABLED=false` 显式关闭 tracing（避免空 endpoint 下 Sender/SpanHandler 仍被实例化、每次 flush 失败丢 span），配置后注入 `true` + `ZIPKIN_ENDPOINT` |
| `config.milvusEnabled` 等 | `false` | RAG 向量检索（可选）：`false` 走 MySQL ngram 全文检索（零依赖）；`true` 需先部署 `k8s/milvus` 并配置 embedding 端点 |
| `storage.enabled` | `true` | 是否创建上传 PVC 并挂到 backend `/data/uploads`（同时是 `/uploads` 静态资源根） |
| `storage.className` | 空（默认存储类） | PVC 存储类；多副本必须 ReadWriteMany（RWX） |
| `storage.accessMode` | `ReadWriteMany` | 上传卷访问模式 |
| `storage.size` | `10Gi` | 上传卷容量 |
| `storage.uploadPath` | `/data/uploads` | 后端上传目录（挂载点），注入 `UPLOAD_PATH` |
| `storage.terminationGracePeriodSeconds` | `60` | Pod 优雅停机宽限期，须 > 后端 prod 关闭超时（30s） |
| `secret.dbPassword/jwtSecret/totpEncryptionKey/aiAuthToken/mcpAuthToken` | 空（必填或走 `existingSecret`） | 数据库密码 / JWT 密钥（≥32 位）/ TOTP 加密密钥（禁止复用 JWT_SECRET）/ AI 鉴权（=后端 `AiServiceConfig.apiKey`）/ MCP 端点令牌 |
| `secret.existingSecret` | 空 | 外部 Secret 名；设置后跳过内部 Secret 创建与空值校验 |
| `ingress.host` | `admin.example.com` | Ingress 域名，生产替换为真实域名 |
| `ingress.tls.enabled` | `false` | 开启后渲染 `tls` 块（secretName `{release}-tls`）与 `cert-manager.io/cluster-issuer` 注解 |
| `ingress.tls.clusterIssuer` | 空 | cert-manager ClusterIssuer/Issuer 名称 |
| `ingress.forceHttps` | `false` | 强制 HTTPS（ingress-nginx `force-ssl-redirect: "true"`） |
| `ingress.proxyBodySize` | `20m` | 上传请求体上限（与后端 multipart 上限 20MB 对齐）；置 `"0"` 不输出该注解 |

## 6. 探针 / PDB / HPA

| 服务 | readiness | liveness | HPA（CPU 70%，min 2 / max 6） | PDB |
| --- | --- | --- | --- | --- |
| backend | `/actuator/health/readiness` | `/actuator/health/liveness` | 有 | `minAvailable: 1` |
| ai | `/readyz`（依赖 Redis，就绪语义） | `/livez`（纯进程存活，恒 200） | 有 | `minAvailable: 1` |
| frontend | `/` :8080 | `/` :8080 | 有 | `minAvailable: 1` |
| website | `/` :8080 | `/` :8080 | 有 | `minAvailable: 1` |

- backend 探针细分自 Actuator probes（`/actuator/health/readiness|liveness`）；ai 的 liveness 不依赖 Redis，避免 Redis 抖动时副本 CrashLoopBackOff。
- 优雅停机：prod `server.shutdown: graceful`（关闭超时 30s），K8s 侧 `terminationGracePeriodSeconds: 60` 兜底，滚动更新时 readiness 先转 Down 摘流量再关闭。

## 7. Ingress 路径与 TLS

Chart 渲染单个 Ingress（`{release}-ingress`），规则（`templates/all.yaml`）：

| 路径 | pathType | 后端 |
| --- | --- | --- |
| `/api` | Prefix | `{release}-backend:8080`（API/SSE 直达，避免二次转发） |
| `/files` | Prefix | `{release}-backend:8080`（受令牌保护的文件内容） |
| `/uploads` | Prefix | `{release}-backend:8080`（历史 /uploads 存量文件地址） |
| `/ws` | Prefix | `{release}-backend:8080`（站内消息 WebSocket，`/ws/messages`） |
| `/` | Prefix | `{release}-frontend:8080`（SPA） |

- 注解为条件渲染：`cert-manager.io/cluster-issuer`（`tls.enabled=true` 时）、`force-ssl-redirect`（`forceHttps=true` 时）、`proxy-body-size`（默认 `20m`，恒输出除非置 `"0"`）。
- Chart 不渲染 SSE 关缓冲注解（无 `proxy-buffering: "off"`）；SSE 实时性须以功能实测为准，需要关缓冲时须自行扩展模板或经注解覆盖（参见部署点检手册 §3.2）。
- 注意：Chart 未为 website 配置 Ingress 规则（仅 Deployment/Service/NetworkPolicy），官网域名如需暴露须另行提供 Ingress/网关指向 `{release}-website`。

## 8. 可观测性

- 指标抓取配置与告警规则：`k8s/monitoring/prometheus.yml`、`prometheus-rules.yml`、`alertmanager.yml`。
- 指标路径：backend `/actuator/prometheus`（指标带 `application=admin-backend` 标签）；**ai-service 为根路径 `/metrics`**（`prometheus.yml` 中该 job 的 `metrics_path=/metrics`，配错为 `/api/v1/metrics` 会抓取 404、`up{job="ai-service"}==0` 误报宕机）。
- 抓取目标以 FQDN 跨命名空间指向业务 Service（`admin-scaffold-backend.admin-scaffold.svc.cluster.local:8080` / `admin-scaffold-ai.admin-scaffold.svc.cluster.local:8000`）；Helm release/命名空间不同时须同步修改 `prometheus.yml` targets。
- Grafana（数据源/看板预置）部署说明见 [`monitoring/grafana/README.md`](monitoring/grafana/README.md)。仓库不提供 Prometheus 服务端清单，需预先部署。

## 9. 常见问题

**私有镜像仓库拉取失败（ImagePullBackOff）**：Chart 未内置 `imagePullSecrets` 参数（以 `values.yaml`/`all.yaml` 为准）。需自行在模板的 Deployment `spec.template.spec.imagePullSecrets` 注入 registry 凭据 Secret（先 `kubectl create secret docker-registry ...`），或把镜像放到集群节点可达的 registry。

**上传 PVC 一直 Pending / 多副本挂载失败**：`storage.className` 留空时用默认存储类；若默认类不支持 RWX，多副本 backend 共享上传卷会失败。按云厂商创建 RWX StorageClass（EFS/NFS/CephFS）后 `--set storage.className=<类名>` 重建 PVC。验证：`kubectl get pvc {release}-upload -n <ns> -o jsonpath='{.status.phase} {.spec.accessModes[0]} {.spec.storageClassName}'`。

**release 名与内部主机名**：`config.callbackBaseUrl/aiBaseUrl/frontendUrl` 留空由模板按 `.Release.Name` 推导，与 Chart 内 Service 命名一致，release 名变化时自动跟随；只有自定义 Service 名或引用集群外地址时才需显式覆盖。AI 回调 SSRF 白名单（`CALLBACK_ALLOWED_ORIGINS`）由 `callbackBaseUrl` 自动推导。

**跨域 403（Invalid CORS request）**：浏览器直接发起的 API 请求被后端 CORS 拒绝，多为 `config.corsAllowedOriginPatterns` 仍是占位域名或与前端实际域名不一致，覆盖为实际域名后 `helm upgrade` 生效。

**AI 指标抓不到 / 误报宕机**：确认 `prometheus.yml` 的 `ai-service` job `metrics_path=/metrics`（根路径）；目标 FQDN 与 release/命名空间一致；Chart 的 NetworkPolicy（`templates/networkpolicy.yaml`）对 ai 放行了 `monitoring` 命名空间的抓取（`namespaceSelector` 匹配 `monitoring`），若监控栈部署在其他命名空间须同步调整该策略；backend 侧 NetworkPolicy 只放行同命名空间来源，跨命名空间抓取/入口流量按实际拓扑核对。
