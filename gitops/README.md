# GitOps 交付说明（Argo CD）

本目录是生产环境的 **Argo CD 交付入口**：用一个 Helm Chart（`k8s/helm/admin-scaffold`）声明 backend / ai-service / frontend / website 四个服务的全部 K8s 资源，Argo CD 只消费该 Chart，Git 提交即触发同步。

## 目录结构与角色

```
gitops/
└── argocd/
    ├── application.yaml     # Argo CD Application：声明从哪个仓库/路径渲染 Helm Chart
    ├── values-prod.yaml     # 生产 values 覆盖（镜像 tag、DB/Redis 端点、域名、密钥策略）
    └── eso-example/
        └── README.md        # External Secrets Operator 密钥注入可复制示例（见下）
```

- Chart 本体位于 `k8s/helm/admin-scaffold`（模板 `templates/all.yaml` + `templates/networkpolicy.yaml`），是 K8s 部署清单的**唯一来源**；`gitops/` 内不放应用原生清单。
- `argocd/application.yaml` 通过 `helm.valueFiles` 引用 `gitops/argocd/values-prod.yaml`（相对路径 `../../../gitops/argocd/values-prod.yaml`），Argo CD 渲染 Chart 时叠加该覆盖。
- 密钥治理：Chart 的 `values.yaml` 中 5 个 `secret.*` 默认留空，未注入时 `templates/all.yaml` 顶部 `fail` 校验直接终止渲染——**Argo CD 会同步失败，直至密钥以安全方式注入**（禁止明文密钥上生产）。注入方式见 [`argocd/eso-example/README.md`](argocd/eso-example/README.md)。

## 部署步骤

前置：集群已安装 Argo CD，并已把目标私有 Git 仓库注册为 Argo CD 的 Repository（SSH/Token 凭据）。

1. **替换占位符**（见下方清单）：改 `argocd/application.yaml` 的 `spec.source.repoURL` 与 `argocd/values-prod.yaml` 中的镜像、DB/Redis、域名与 TLS 配置。
2. **提交到 Git**：占位符全部替换后再提交，Argo CD 监听 `main` 分支。
3. **创建 Application**（幂等，重复执行无害）：

   ```bash
   kubectl apply -f gitops/argocd/application.yaml
   ```

4. **验证同步**：

   ```bash
   argocd app get admin-scaffold            # Health/Sync 状态
   argocd app sync admin-scaffold           # 需要时手动触发
   kubectl -n admin-scaffold get pods,ingress,secret
   ```

Application 自带 `automated: { prune: true, selfHeal: true }` 与 `CreateNamespace=true`，目标 namespace 为 `admin-scaffold`。**生产变更一律走 Git 提交**：手工 `kubectl apply` 的零散资源会被 prune/selfHeal 视为漂移并回滚。

## 镜像 tag 更新流程

镜像由 CI 构建并（配置了 `DOCKER_REGISTRY` 时）推送，tag 为**不可变的 commit sha**（同时追加 `latest`）。Argo CD 实际部署的版本由 `values-prod.yaml` 的 `images.*` 决定，两者之间的衔接目前是**人工维护 values**：

1. CI 全绿并已把镜像推到仓库（如 `<registry>/admin-backend:<sha>`）。
2. 更新 `gitops/argocd/values-prod.yaml`：

   ```yaml
   images:
     backend:  <registry>/admin-backend:<sha>
     ai:       <registry>/ai-service:<sha>
     frontend: <registry>/admin-frontend:<sha>
     website:  <registry>/website:<sha>
   ```

3. 提交并合入 `main` → Argo CD 检测到变更后自动同步（不发布版本或需回滚时，回滚上一个 Git 提交即可）。

自动化的方向（均未在仓库内实现，需评估后引入）：

- **Argo CD Image Updater**：在 Application 上加 `argocd-image-updater.argoproj.io/*` 注解，按镜像名自动写回 `images.*`（可配 `allow-tags` 只接受 `^[0-9a-f]{40}$` 的 sha tag 或语义化 tag）。
- **CI 机器人提交（GitOps 优先）**：流水线发版后由机器人（Updatecli / Renovate / 自研脚本）自动 bump `values-prod.yaml` 的 tag 并提交 PR，走与人工一致的评审与审计。
- **摘要级引用**：Chart 侧把 `images.*` 收敛到统一 `imagePullPolicy` 约定并启用 digest 引用（`@sha256:...`），需要先在 CI 里落地“构建产物 sha 摘要随版本记录”。

## 上线前占位符替换清单（必须全部完成）

| 文件 | 占位符 | 说明 |
| --- | --- | --- |
| `argocd/application.yaml` | `spec.source.repoURL: https://github.com/your-org/admin-scaffold.git` | 替换为真实私有 Git 仓库地址；`path` 与 `valueFiles` 的相对布局保持不变 |
| `argocd/values-prod.yaml` | `images.backend/ai/frontend/website: registry.example.com/*:1.0.0` | 替换为真实镜像仓库与不可变 tag |
| `argocd/values-prod.yaml` | `config.dbHost: mysql.example.internal`、`dbUser`、`redisHost` 等 | 替换为真实 DB/Redis 端点；服务名与默认不同时同步核对 `callbackBaseUrl` / `aiBaseUrl` / `frontendUrl` 的推导值 |
| `argocd/values-prod.yaml` | `ingress.host: admin.example.com` | 替换为真实域名；**同步覆盖 `config.corsAllowedOriginPatterns`**（Chart 默认 `https://admin.example.com`，CORS 与域名不一致会 403） |
| `argocd/values-prod.yaml` | `secret.*` 保持空串 | 密钥禁止入仓：ESO / 手工 Secret 注入后设置 `secret.existingSecret`（示例见下） |

**TLS 必开**：Chart 默认 `ingress.tls.enabled: false`（HTTP 明文），`values-prod.yaml` 未覆盖 TLS。上线前必须在生产 values 中显式开启：

```yaml
ingress:
  host: <真实域名>
  tls:
    enabled: true
    clusterIssuer: letsencrypt-prod   # 或集群内其他 cert-manager Issuer
  forceHttps: true                    # ingress-nginx 强制跳转 HTTPS
  proxyBodySize: "20m"                # 与后端 20MB 上传上限对齐（默认已注入）
config:
  corsAllowedOriginPatterns: https://<真实前端域名>
```

## 密钥注入

- 可复制落地示例：**方案 A（ESO SecretStore + ExternalSecret，自动轮换）** 与 **方案 B（手工 Secret）** 见 [`argocd/eso-example/README.md`](argocd/eso-example/README.md)。
- 接入后把 `values-prod.yaml` 置 `secret.existingSecret: <Secret 名>`，Chart 跳过空值校验、`envFrom` 直接引用外部 Secret，Argo CD 同步不再因空密钥失败。
- 密钥一致性：外部 Secret 的 5 个键名（`DB_PASSWORD` / `JWT_SECRET` / `TOTP_ENCRYPTION_KEY` / `AUTH_TOKEN` / `MCP_AUTH_TOKEN`）必须与 Chart `secret.*` 及后端配置保持一致，任一不一致会出现 401/403/连库失败。

## 环境分层建议

当前仓库只交付一条生产链路（一个 Application + 一份 `values-prod.yaml`）。需要多环境时建议按同一套 Chart 做 values 分层：

- **每环境一份 values**：`values-dev.yaml` / `values-staging.yaml` / `values-prod.yaml`，每环境一个 Application（destination namespace 区分，如 `admin-scaffold-dev` / `-prod`），`targetRevision` 指向对应分支或 tag；测试环境不需要 ESO，可直接手工 Secret。
- **prod 只收可信来源**：生产 values 走独立私有仓库 + 分支保护，密钥字段恒空、只经注入生效；版本晋升（同一镜像 tag 从 staging 推到 prod）建议由流水线推进而非手工。
- 多集群/多租户时可演进为 **app-of-apps**：一个“交付仓库”只放 Application CR，指向实际渲染目标。

## 相关文档

- Chart 参数全表：`k8s/helm/admin-scaffold/values.yaml`（含 `secret.existingSecret`、`ingress.tls`、`config.*` 的注释）
- 集群前置（metrics-server / ingress / RWX / cert-manager / ESO）：`k8s/README.md`
