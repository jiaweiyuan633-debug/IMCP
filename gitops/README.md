# GitOps 交付

本目录提供 ArgoCD 声明式交付配置。

## 使用步骤

1. 将镜像推送到生产镜像仓库。
2. 修改 `argocd/values-prod.yaml` 中的镜像、数据库、Redis、密钥和域名。
3. 将 `argocd/application.yaml` 提交到仓库。
4. 在 ArgoCD 中创建 Application，或执行：

```bash
kubectl apply -f gitops/argocd/application.yaml
```

ArgoCD 会监听 `main` 分支，自动同步 `k8s/helm/admin-scaffold`，并开启 `prune/selfHeal`。

> **Helm Chart 是 K8s 部署的唯一来源**（原生清单已移除），ArgoCD 仅消费该 Chart；`values-prod.yaml` 中的 `secret.*` 由部署时注入（见下）。

当前工程已完成 Playwright E2E、覆盖率门槛、PWA 离线缓存、API 版本化与 GitOps 交付。

## 生产要求

- `values-prod.yaml` 中 `secret.*` 必须保持为空，密钥由部署时注入（ArgoCD `helm.parameters`、External Secrets Operator、Vault、Sealed Secrets 等）。留空时 Helm 模板将 fail-fast，ArgoCD 同步失败直至密钥注入完成，杜绝明文密钥上生产。
- 建议将 `values-prod.yaml` 放入独立私有仓库。
- 每次发布通过 Git 提交触发，禁止手工 `kubectl apply` 修改生产。
