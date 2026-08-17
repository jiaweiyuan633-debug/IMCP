# Grafana（批次 F 可观测性）

本目录提供平台 Grafana 的部署清单与预置配置。配置以独立文件为**单一来源**（不内联进 manifest，避免两份声明漂移），与 `k8s/monitoring` 其余文件风格一致：

| 文件 | 用途 |
| --- | --- |
| `grafana.yaml` | Grafana Deployment + Service（挂载下方三个 ConfigMap 实现预置） |
| `provisioning/datasources.yml` | Prometheus 数据源预置（uid 固定为 `prometheus`，dashboard 静态引用） |
| `provisioning/dashboards.yml` | Dashboard 提供方：扫描 `/var/lib/grafana/dashboards` 自动加载 |
| `dashboards/admin-scaffold.json` | 平台可观测性总览 dashboard（后端 JVM/HTTP/连接池 + AI 服务） |

## 部署

前提：

- 目标集群须已存在 `monitoring` 命名空间（第 0 步创建，幂等）。
- Prometheus 已部署于 `monitoring` 命名空间（数据源 URL 同命名空间短名 `http://prometheus:9090`）。仓库仅提供采集/告警配置与 Grafana 数据源，不含 Prometheus 服务端清单，需预先部署 Prometheus（本地演示：`docker run -d --name prometheus -p 9090:9090 -v "$(pwd)/k8s/monitoring:/etc/prometheus:ro" prom/prometheus --config.file=/etc/prometheus/prometheus.yml`；生产：`helm repo add prometheus-community https://prometheus-community.github.io/helm-charts && helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring`）。
- `prometheus.yml` 采集目标以 FQDN 跨命名空间指向业务 Service（见 `k8s/monitoring/prometheus.yml` 头部注释），与 Grafana 数据源两个前提自洽。

```bash
# 0. 命名空间（首次部署必做；幂等，已存在时跳过）
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

# 1. 预置 ConfigMap（幂等：配置变更后重跑即可热更新；须与 Deployment 同命名空间）
kubectl create configmap grafana-datasources -n monitoring \
  --from-file=provisioning/datasources.yml --dry-run=client -o yaml | kubectl apply -f -
kubectl create configmap grafana-dashboard-provider -n monitoring \
  --from-file=provisioning/dashboards.yml --dry-run=client -o yaml | kubectl apply -f -
kubectl create configmap grafana-dashboards -n monitoring \
  --from-file=dashboards/ --dry-run=client -o yaml | kubectl apply -f -

# 2. 管理员口令（可选：不建则回落 Grafana 默认 admin/admin，生产必须创建）
kubectl create secret generic grafana-admin --from-literal=admin-password='<强口令>' -n monitoring

# 3. 部署
kubectl apply -f grafana.yaml

# 4. 端口转发访问 http://localhost:3000
kubectl -n monitoring port-forward svc/grafana 3000:3000
```

## 已并入现有 Grafana

若团队已有 Grafana，无需部署本清单，仅需导入两处：

1. **数据源**：按 `provisioning/datasources.yml` 创建 Prometheus 数据源，并把 uid 固定为 `prometheus`（或改 dashboard JSON 中所有 `"datasource":{"uid":"prometheus"}`）。
2. **Dashboard**：Grafana UI → Dashboards → New → Import → 粘贴 `dashboards/admin-scaffold.json`。

## 指标说明

- 后端（Spring Boot + Micrometer）：`/actuator/prometheus`，指标带 `application=admin-backend` 标签。
- AI 服务（FastAPI + prometheus_client）：`/metrics`（根路径，`prometheus.yml` 中该 job 的 `metrics_path` 为 `/metrics`；批次5·R4-1.51 修正了此前误述的 `/api/v1/metrics`——与代码不符会抓取 404 误报服务宕机）。
- 跨命名空间：`prometheus.yml` 的抓取目标用全限定名（`admin-scaffold-backend.admin-scaffold.svc.cluster.local:8080`），Prometheus 部署于 `monitoring` 命名空间、跨命名空间抓取 `admin-scaffold` 命名空间的业务 Service（Helm release=admin-scaffold）；若 release/命名空间不同，需同步修改 `prometheus.yml` 的 targets FQDN。
- AI 服务目前仅暴露进程级指标（CPU/RSS/版本/存活），业务自定义指标可后续在 `app/api/routes.py` 的 `/metrics` 端点补充。
