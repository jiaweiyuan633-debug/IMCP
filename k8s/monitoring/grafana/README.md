# Grafana（可观测性可视化）

本目录提供平台 Grafana 的部署清单与预置配置。配置以独立文件为**单一来源**（不内联进 manifest，避免两份声明漂移），与 `k8s/monitoring` 其余文件风格一致：

| 文件 | 用途 |
| --- | --- |
| `grafana.yaml` | Grafana Deployment + Service（挂载下方三个 ConfigMap 实现预置） |
| `provisioning/datasources.yml` | Prometheus 数据源预置（uid 固定为 `prometheus`，dashboard 静态引用） |
| `provisioning/dashboards.yml` | Dashboard 提供方：扫描 `/var/lib/grafana/dashboards` 自动加载 |
| `dashboards/admin-scaffold.json` | 平台可观测性总览 dashboard（后端 JVM/HTTP/连接池 + AI 服务） |

## 1. 部署前置

- 目标集群已存在 `monitoring` 命名空间（部署第 0 步创建，幂等）。
- **Prometheus 已部署于 `monitoring` 命名空间**：数据源 URL 用同命名空间短名 `http://prometheus:9090`。仓库仅提供采集/告警配置与 Grafana 数据源，**不含 Prometheus 服务端清单**，需预先部署。本地演示：

  ```bash
  docker run -d --name prometheus -p 9090:9090 \
    -v "$(pwd)/k8s/monitoring:/etc/prometheus:ro" \
    prom/prometheus --config.file=/etc/prometheus/prometheus.yml
  ```

  生产示例：`helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring`（名称/形态可按实际部署调整）。

- Prometheus 采集目标（`k8s/monitoring/prometheus.yml`）以 FQDN 跨命名空间指向业务 Service（`admin-scaffold-backend.admin-scaffold.svc.cluster.local:8080` 等，Helm release=admin-scaffold、业务命名空间 `admin-scaffold`）；Helm release 或命名空间不同时须同步修改 targets FQDN。

## 2. 部署

```bash
# 0. 命名空间（首次部署必做；幂等，已存在时跳过）
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

# 1. 预置 ConfigMap（幂等：配置变更后重跑同款命令即可热更新；须与 Deployment 同命名空间）
kubectl create configmap grafana-datasources -n monitoring \
  --from-file=provisioning/datasources.yml --dry-run=client -o yaml | kubectl apply -f -
kubectl create configmap grafana-dashboard-provider -n monitoring \
  --from-file=provisioning/dashboards.yml --dry-run=client -o yaml | kubectl apply -f -
kubectl create configmap grafana-dashboards -n monitoring \
  --from-file=dashboards/ --dry-run=client -o yaml | kubectl apply -f -

# 2. 管理员口令（不建则回落 Grafana 默认 admin/admin，生产必须创建）
kubectl create secret generic grafana-admin --from-literal=admin-password='<强口令>' -n monitoring

# 3. 部署
kubectl apply -f grafana.yaml

# 4. 端口转发访问 http://localhost:3000
kubectl -n monitoring port-forward svc/grafana 3000:3000
```

## 3. provisioning 机制

- **数据源**：`provisioning/datasources.yml` 挂载到 `/etc/grafana/provisioning/datasources`，Grafana 启动即注册 Prometheus 数据源。`uid` 固定为 `prometheus`，dashboard JSON 内按 uid 静态引用，无需模板变量；`editable: false` 防止 UI 误改。
- **看板提供方**：`provisioning/dashboards.yml` 挂载到 `/etc/grafana/provisioning/dashboards`，声明扫描目录 `/var/lib/grafana/dashboards`（由 `grafana-dashboards` ConfigMap 以 `--from-file=dashboards/` 挂载）；`updateIntervalSeconds: 30` 使 JSON 变更在约 30s 内自动热加载，无需重启。
- 配置变更流程：改文件 → 重跑第 1 步的 `kubectl create configmap ... --dry-run=client -o yaml | kubectl apply -f -`（ConfigMap 内容更新）→ 数据源/看板按上述机制自动生效。

## 4. 已并入现有 Grafana

若团队已有 Grafana，无需部署本清单，仅需导入两处：

1. **数据源**：按 `provisioning/datasources.yml` 创建 Prometheus 数据源，并把 uid 固定为 `prometheus`（或改 dashboard JSON 中所有 `"datasource":{"uid":"prometheus"}`）。
2. **看板**：Grafana UI → Dashboards → New → Import → 粘贴 `dashboards/admin-scaffold.json`（提供方文件夹名 `admin-scaffold` 可选）。

## 5. 指标路径

| 目标 | 指标端点 | 说明 |
| --- | --- | --- |
| backend | `/actuator/prometheus` | Spring Boot + Micrometer，指标带 `application=admin-backend` 标签 |
| ai-service | `/metrics`（根路径） | FastAPI + prometheus_client，端点定义于 `ai-service/app/main.py` |

- **ai-service 的抓取端点是根 `/metrics`**：`prometheus.yml` 中 `ai-service` job 的 `metrics_path=/metrics`。不要配成 `/api/v1/metrics` 之类前缀路径——与代码不符会抓取 404，导致 `up{job="ai-service"}==0` 误报服务宕机。
- 跨命名空间：监控栈（Prometheus + Grafana）位于 `monitoring` 命名空间，`prometheus.yml` 的抓取目标用全限定名指向 `admin-scaffold` 命名空间的业务 Service；若 Helm release/命名空间不同，需同步修改 targets FQDN。
- AI 服务当前指标构成：prometheus_client 默认的进程级指标（`process_*` / `python_*`）+ 任务生命周期计数器与队列深度 gauge（`ai_task_*` / `ai_queue_depth` / `ai_worker_count`，定义于 `ai-service/app/core/metrics.py`，抓取时实时采样队列深度；Redis 不可用时队列深度置 -1 表示未知，`/metrics` 仍返回 200）。新增业务指标在 `app/core/metrics.py` 注册后经 `/metrics` 暴露。
