# 生产密钥注入可落地示例（批次6·R4-1.52）
#
# 背景：values-prod.yaml 的 secret.* 恒为空 → Chart 顶部 fail 校验使 ArgoCD 同步永远失败，
# 文档仅说"由 helm.parameters/ESO/Vault 注入"但无可复制配置。本目录提供两种方案：
#
#   方案 A（推荐）：External Secrets Operator（ESO）——Secret 由 SecretStore 从云 KMS/
#   Vault/Secrets Manager 同步，GitOps 全自动、密钥不落 Git、支持轮换。
#   方案 B：手工 Secret——一次性创建，简单直接；轮换需人工更新。
#
# 无论哪种方案，部署 Chart 时设置 secret.existingSecret=<secret 名> 即可：
#   helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
#     --namespace admin-scaffold --create-namespace \
#     --set secret.existingSecret=admin-scaffold-secret
# 或经 ArgoCD values 覆盖（见 application.yaml 的 valueFiles）。

# =====================================================================
# 方案 A：External Secrets Operator（推荐）
# =====================================================================
# 前置：集群已安装 ESO（helm repo add external-secrets https://charts.external-secrets.io
#       && helm upgrade --install external-secrets external-secrets/external-secrets \
#         -n external-secrets --create-namespace）
#
# 1) SecretStore：声明密钥来源（此处示例 AWS Secrets Manager；Vault/GCP/Azure 同理）
#    apiVersion: external-secrets.io/v1beta1
#    kind: SecretStore
#    metadata:
#      name: admin-scaffold-store
#      namespace: admin-scaffold
#    spec:
#      provider:
#        aws:
#          service: SecretsManager
#          region: ap-southeast-1
#          # auth 需配置 IRSA/static 凭据，见 ESO 文档
#          auth:
#            secretRef:
#              accessKeyIDSecretRef: { name: eso-aws-creds, key: access-key }
#              secretAccessKeySecretRef: { name: eso-aws-creds, key: secret-access-key }
#
# 2) ExternalSecret：把云密钥映射为 k8s Secret（键名与 Chart envFrom 引用一致）
#    apiVersion: external-secrets.io/v1beta1
#    kind: ExternalSecret
#    metadata:
#      name: admin-scaffold-secret
#      namespace: admin-scaffold
#    spec:
#      refreshInterval: 1h          # 轮换周期：云侧改密后 1h 内同步
#      secretStoreRef:
#        name: admin-scaffold-store
#        kind: SecretStore
#      target:
#        name: admin-scaffold-secret  # 生成的 Secret 名（配 Chart secret.existingSecret）
#        creationPolicy: Owner
#      data:
#        - secretKey: DB_PASSWORD
#          remoteRef: { key: admin-scaffold/db-password }
#        - secretKey: JWT_SECRET
#          remoteRef: { key: admin-scaffold/jwt-secret }
#        - secretKey: TOTP_ENCRYPTION_KEY
#          remoteRef: { key: admin-scaffold/totp-encryption-key }
#        - secretKey: AUTH_TOKEN
#          remoteRef: { key: admin-scaffold/ai-auth-token }
#        - secretKey: MCP_AUTH_TOKEN
#          remoteRef: { key: admin-scaffold/mcp-auth-token }

# =====================================================================
# 方案 B：手工 Secret（无 ESO 时的最小可用方案）
# =====================================================================
# kubectl -n admin-scaffold create secret generic admin-scaffold-secret \
#   --from-literal=DB_PASSWORD='<db-password>' \
#   --from-literal=JWT_SECRET='<32+ 位随机密钥>' \
#   --from-literal=TOTP_ENCRYPTION_KEY='<32+ 位随机密钥>' \
#   --from-literal=AUTH_TOKEN='<与后端 AiServiceConfig.apiKey 一致>' \
#   --from-literal=MCP_AUTH_TOKEN='<MCP 鉴权令牌>'
# 生成随机密钥建议：
#   openssl rand -base64 48   （JWT_SECRET / TOTP_ENCRYPTION_KEY 各生成一个）

# =====================================================================
# 轮换说明
# =====================================================================
# - ESO：改云侧密钥 → refreshInterval 到期自动同步，无需重启（环境变量随 Secret 更新，
#   Spring 对 DB/JWT 等会按需重连；JWT_SECRET 轮换需滚动重启后端使新密钥生效）。
# - 手工 Secret：kubectl edit secret 后滚动重启相关 Deployment：
#   kubectl -n admin-scaffold rollout restart deployment/admin-scaffold-backend
# - 轮换后验证四处一致：DB_PASSWORD / JWT_SECRET / TOTP_ENCRYPTION_KEY / AUTH_TOKEN，
#   任一不一致会出现 401/403/连库失败等疑难杂症。
