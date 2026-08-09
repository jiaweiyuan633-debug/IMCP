# Kubernetes 部署

1. 先部署 MySQL/Redis（可用 Helm 或云托管服务）。
2. 替换镜像地址和 `admin-config` 中的密钥。
3. 执行：

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

生产环境建议使用 Helm 管理版本、HPA 自动扩缩容、Secrets 保存密码，并通过 Ingress 暴露 HTTPS。

