# 密钥注入可落地示例（External Secrets Operator 与手工 Secret）

## 背景

`k8s/helm/admin-scaffold/values.yaml` 中 5 个密钥默认留空，模板 `templates/all.yaml` 顶部做了 fail 校验：只要 `secret.existingSecret` 未设置且任一 `secret.dbPassword / jwtSecret / totpEncryptionKey / aiAuthToken / mcpAuthToken` 为空，渲染立即终止。因此在纯 GitOps 场景下，**Argo CD 同步会一直失败，直到密钥以"不落 Git"的方式注入**。

本目录给出两种可复制的注入方案：

- **方案 A（推荐）**：External Secrets Operator（ESO）——Secret 由 `SecretStore` 从云 KMS / Vault / Secrets Manager 同步，GitOps 全自动、密钥不落 Git、支持周期轮换。
- **方案 B**：手工创建 `Secret`——一次性创建，简单直接；轮换需人工更新。

两种方案最终都只做一件事：**让目标 namespace 里存在一个含下述 5 个键的 Secret，并把 Chart 指向它**：

```text
DB_PASSWORD
JWT_SECRET
TOTP_ENCRYPTION_KEY
AUTH_TOKEN
MCP_AUTH_TOKEN
```

键名必须与 Chart `values.yaml` 的 `secret.*` 及后端 `envFrom` 引用保持一致（`all.yaml` 中 backend / ai-service 两个 Deployment 的 `envFrom.secretRef` 直接引用该 Secret；`SPRING_PROFILES_ACTIVE=prod` 下缺键会启动失败）。

## 接 Chart 的唯一参数：`secret.existingSecret`

无论哪种方案，部署时设置 `secret.existingSecret=<Secret 名>` 即可让 Chart 跳过空值校验、不创建内部 Secret，改为 `envFrom` 引用外部 Secret：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace \
  --set secret.existingSecret=admin-scaffold-secret
```

经 Argo CD 交付时（`gitops/argocd/application.yaml` 的 `valueFiles` 指向 `gitops/argocd/values-prod.yaml`），在 values 文件里写：

```yaml
# gitops/argocd/values-prod.yaml
secret:
  dbPassword: ""          # 保持为空
  jwtSecret: ""
  totpEncryptionKey: ""
  aiAuthToken: ""
  mcpAuthToken: ""
  existingSecret: admin-scaffold-secret   # ← 指向 ESO / 手工创建的 Secret
```

> Chart 未设置 `existingSecret` 时内部 Secret 名为 `{release}-secret`（release 为 `admin-scaffold` 时即 `admin-scaffold-secret`）。若 ESO 也生成同名 Secret 而 values 未指向它，会与 Chart 自建 Secret 冲突，务必显式设置 `secret.existingSecret`。

## 方案 A：External Secrets Operator（推荐）

### 前置组件

集群已安装 ESO：

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm upgrade --install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace
```

### 1) SecretStore：声明密钥来源

以下以 AWS Secrets Manager 为例；Vault / GCP / Azure 的 provider 配置见 [ESO 官方文档](https://external-secrets.io/latest/provider/aws-secrets-manager/)。

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: admin-scaffold-store
  namespace: admin-scaffold
spec:
  provider:
    aws:
      service: SecretsManager
      region: ap-southeast-1
      # auth 需配置 IRSA / static 凭据（见 ESO 文档）；此处为 static 凭据示例
      auth:
        secretRef:
          accessKeyIDSecretRef: { name: eso-aws-creds, key: access-key }
          secretAccessKeySecretRef: { name: eso-aws-creds, key: secret-access-key }
```

> 建议生产用 IRSA / Workload Identity，避免在集群里再放一组静态 AWS 凭据。

### 2) ExternalSecret：把云密钥映射为 k8s Secret

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: admin-scaffold-secret
  namespace: admin-scaffold
spec:
  refreshInterval: 1h          # 轮换周期：云侧改密后 1h 内同步到 k8s Secret
  secretStoreRef:
    name: admin-scaffold-store
    kind: SecretStore
  target:
    name: admin-scaffold-secret   # 生成的 Secret 名（配 Chart secret.existingSecret）
    creationPolicy: Owner
  data:
    - secretKey: DB_PASSWORD
      remoteRef: { key: admin-scaffold/db-password }
    - secretKey: JWT_SECRET
      remoteRef: { key: admin-scaffold/jwt-secret }
    - secretKey: TOTP_ENCRYPTION_KEY
      remoteRef: { key: admin-scaffold/totp-encryption-key }
    - secretKey: AUTH_TOKEN
      remoteRef: { key: admin-scaffold/ai-auth-token }
    - secretKey: MCP_AUTH_TOKEN
      remoteRef: { key: admin-scaffold/mcp-auth-token }
```

云侧键名（`admin-scaffold/db-password` 等）只在本文件维护，两者一一对应即可。

## 方案 B：手工 Secret（无 ESO 时的最小可用方案）

```bash
kubectl -n admin-scaffold create secret generic admin-scaffold-secret \
  --from-literal=DB_PASSWORD='<db-password>' \
  --from-literal=JWT_SECRET='<32+ 位随机密钥>' \
  --from-literal=TOTP_ENCRYPTION_KEY='<32+ 位随机密钥>' \
  --from-literal=AUTH_TOKEN='<与后端 AiServiceConfig.apiKey 一致>' \
  --from-literal=MCP_AUTH_TOKEN='<MCP 鉴权令牌>'
```

生成随机密钥建议（JWT_SECRET / TOTP_ENCRYPTION_KEY 各生成一个）：

```bash
openssl rand -base64 48
```

创建后同样设置 `secret.existingSecret=admin-scaffold-secret`（见上文）。

## 轮换步骤

注意：**Secret 更新不会自动注入到运行中 Pod**——Deployment 的 `envFrom` 只在容器启动时读取一次。无论哪种方案，改密后都需要滚动重启消费该 Secret 的 Deployment（backend 与 ai-service）：

```bash
# 1) 方案 A：改云侧密钥 → 等 refreshInterval 到期（或手动触发）
#    方案 B：kubectl edit secret 更新键值
kubectl -n admin-scaffold edit secret admin-scaffold-secret

# 2) 让运行中的容器重新读取新密钥
kubectl -n admin-scaffold rollout restart deployment/admin-scaffold-backend
kubectl -n admin-scaffold rollout restart deployment/admin-scaffold-ai
```

- **JWT_SECRET** 轮换后所有旧 token 立即失效，用户需重新登录（属预期行为），可在低峰窗口执行。
- 轮换后核对一致性：外部 Secret 的 5 个键与 Chart `secret.*` / 后端配置必须一致。任一不一致会出现 401 / 403 / 连库失败等疑难症状，先对照本文件键名排障。
- 验证命令：

```bash
kubectl -n admin-scaffold get secret admin-scaffold-secret -o jsonpath='{.data}' | tr ',' '\n'
kubectl -n admin-scaffold get externalsecret admin-scaffold-secret   # ESO：检查 Ready/SecretSynced 状态
```

## 与 fail-fast 校验的关系

- 未设置 `secret.existingSecret` 且任一 `secret.*` 为空 → `all.yaml` 顶部 `fail` 终止渲染，Argo CD 同步失败（这是有意的安全兜底，不是故障）。
- 设置 `secret.existingSecret` 后跳过该校验，前提是目标 Secret 真实存在且键齐全；若 Secret 缺失，Deployment 会因 `envFrom` 找不到引用而无法启动。
