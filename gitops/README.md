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

当前工程已完成 Playwright E2E、覆盖率门槛、PWA 离线缓存、API 版本化与 GitOps 交付。

## 生产要求

- Secret 中的值只用于占位，生产应使用外部 Secret 或 Sealed Secrets。
- 建议将 `values-prod.yaml` 放入独立私有仓库。
- 每次发布通过 Git 提交触发，禁止手工 `kubectl apply` 修改生产。
