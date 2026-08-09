# Kubernetes 部署

本目录提供最小可用 K8s 清单：

- `configmap.yaml`：后端与 AI 服务公共环境配置
- `manifests.yaml`：backend / ai-service / frontend Deployment、Service、Ingress

使用步骤：

1. 先部署 MySQL/Redis（可选用云托管或 Helm）。
2. 替换镜像地址、ConfigMap 中的密码和 Ingress Host。
3. 执行：

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

生产建议：

- 密码放入 Secret
- 配置 HPA 自动扩缩容
- 使用 HTTPS Ingress 和外部负载均衡
- 日志采集到 Loki/ELK，指标接入 Prometheus + Grafana

