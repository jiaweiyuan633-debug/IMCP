# 上线前目标集群落地点检手册（Go/No-Go · 部署组 B）

> 适用：对照 [`docs/release-review-2025.md`](../release-review-2025.md) 第七节「B. 部署（必须全绿）」第 198-206 行，对**目标生产集群**逐项执行落地验收。
> **判定规则：B 组全部为 Yes 才允许对外投产；任一项 FAIL（No）即 No-Go，先整改再复检。**
> 配套半自动预检：`scripts/verify-cluster.ps1`（只做无需填接收人、无需触发真实告警的可判读检查，任一 FAIL 退出码 1），其 `[PASS]/[FAIL]` 输出可直接并入本文记录表「结果」列。
> 本文只读调研 + 新增本文件与 `scripts/verify-cluster.ps1`，未改动任何既有文件内容。

B 组 8 项原文与对应落地小节：

| # | release-review 检查项 | 本手册小节 |
| --- | --- | --- |
| B1 | Helm Ingress 已启用 TLS（cert-manager + force-ssl-redirect） | §3 |
| B2 | /api、/files、/uploads、/ws 四条路径正确路由；SSE 代理已关缓冲 | §3 |
| B3 | 上传链路实测 >1MB 成功（proxy-body-size / client_max_body_size） | §3 |
| B4 | Prometheus 抓取 backend /actuator/prometheus 与 ai /metrics 均 200，无 AIServiceDown 误报 | §4 |
| B5 | Alertmanager 真实接收器已验证发送成功 | §4 |
| B6 | 备份已定时执行 + 异地副本 + 至少一次 backup-drill 演练，RPO/RTO 有记录 | §5 |
| B7 | ArgoCD/GitOps 链路按文档可独立跑通（密钥注入有可复制示例） | §6 |
| B8 | metrics-server / ingress-controller / RWX 存储类 / cert-manager 四件套已确认存在 | §2 |

前置事实（已按仓库现状核实，验收命令中的名称/路径以它们为基准）：

- Helm release 默认 `admin-scaffold`、命名空间 `admin-scaffold`（`gitops/argocd/application.yaml`、`k8s/helm/admin-scaffold`）；下文命令用变量 `$rel=admin-scaffold`、`$ns=admin-scaffold`。
- 监控栈（Prometheus/Grafana/Alertmanager）位于 `monitoring` 命名空间，`prometheus.yml` 抓取目标以 FQDN 跨命名空间指向业务 Service（见 §4）。
- Ingress 名 `{release}-ingress`；TLS 证书 Secret 名 `{release}-tls`；上传 PVC 名 `{release}-upload`。

---

## 1. 判定规则汇总与记录表

### 1.1 必过项（任一 FAIL → No-Go）

1. **四件套缺一即 No-Go**（§2）：metrics-server / ingress-controller / RWX 存储类 / cert-manager。
2. B 组其余 7 项逐条按 §3-§6 验收，**任一 FAIL（No）即 No-Go**。
3. 涉及"占位/需人工填真值"的项（§4 Alertmanager 接收人、§6 ESO/密钥注入）在**未填真前一律按 No 处理**——占位即静默漏报/同步失败，不算已落地。
4. 存在本文列出的「悬空项」（§8）时，该项记录表结果须写 `待人工确认` 并附证据，不得自行判 Yes。

### 1.2 半自动预检如何并入

- 在目标集群执行 `scripts/verify-cluster.ps1 -KubeContext <prod-context>`（详见脚本头部与 `docs/deploy/README.md`、`docs/runbook.md` 用法）。
- 脚本覆盖：四件套（metrics-server 就绪 + `kubectl top nodes`、RWX 存储类/已绑定 RWX PVC、ingress-nginx controller 就绪、cert-manager 三 Deployment + Issuer/ClusterIssuer Ready）、monitoring 三组件部署状态（`-SkipMonitor` 可跳过）、ArgoCD Application 状态（不存在仅提示）。
- 脚本输出逐行 `[PASS]/[FAIL] 检查名 —— 说明`；**任一 FAIL 即 exit 1**。把这些行按检查项贴入 1.3 记录表的「结果/证据链接」列即完成并入。
- 脚本**不覆盖**需要人工的项：TLS 证书与 Ingress 路由实测（§3，含 SSE/上传实测）、Prometheus 抓取 200（§4.1，可选人工复核）、Alertmanager 真实接收人填写与测试告警（§4.2）、备份定时/异地/drill/RPO-RTO 记录（§5）、ESO/密钥 fail-fast 落地证据（§6）。这些必须人工逐项执行并记录。

### 1.3 记录表模板（每次复检复制一行；日期以验收当天为准）

| 日期 | 操作人 | 检查项（对应 §） | 结果（Yes/No/待确认） | 备注（判读依据） | 证据链接（命令输出/截图/工单/备份产物路径） |
| --- | --- | --- | --- | --- | --- |
|  |  | §2 metrics-server |  |  |  |
|  |  | §2 ingress-controller |  |  |  |
|  |  | §2 RWX 存储类 |  |  |  |
|  |  | §2 cert-manager |  |  |  |
|  |  | §3 Ingress TLS |  |  |  |
|  |  | §3 四条路径路由 + SSE 关缓冲实测 |  |  |  |
|  |  | §3 >1MB 上传实测 |  |  |  |
|  |  | §4 Prometheus 两目标 200 |  |  |  |
|  |  | §4 Alertmanager 真实接收器测试 |  |  |  |
|  |  | §5 备份定时执行 |  |  |  |
|  |  | §5 异地副本 |  |  |  |
|  |  | §5 backup-drill 演练 |  |  |  |
|  |  | §5 RPO/RTO 记录 |  |  |  |
|  |  | §6 ArgoCD 同步健康 |  |  |  |
|  |  | §6 ESO SecretStore/密钥 fail-fast |  |  |  |

**最终判定：B 组 ____ 项 Yes / ____ 项 No / ____ 项待确认 → Go / No-Go**（存在任一 No 或待确认未消项即 No-Go）。

---

## 2. 集群前置四件套（B8，缺一即 No-Go）

> Chart 的 `values.yaml` 默认值：`storage.enabled=true`、`storage.accessMode=ReadWriteMany`、`storage.className=""`（默认存储类）、`storage.size=10Gi`；HPA 依赖 metrics-server；Ingress TLS 依赖 cert-manager 与 issuer。四件套的安装命令见 [`k8s/README.md`](../../k8s/README.md#集群前置四件套批次6r4-152缺一即静默降级或功能失败)。

### 2.1 metrics-server（HPA 依赖）

**验收命令**：

```bash
kubectl get deployment metrics-server -n kube-system   # READY 1/1
kubectl top nodes                                      # 应输出 NODE/CPU(%)/MEMORY(%) 表
kubectl get apiservice v1beta1.metrics.k8s.io          # AVAILABLE True
```

**预期判读**：三命令均成功且 `kubectl top nodes` 有非空输出 → PASS。仅 Deployment Ready 而 `top nodes` 空/报错（如 `Metrics API not available`）→ FAIL：metrics-server 未真正服务。

**常见失败处置**：镜像拉取/证书问题先看 `kubectl logs -n kube-system deploy/metrics-server`；内网自签 CA 场景需 `--kubelet-insecure-tls` 参数；确认 kubelet 10350 端口可达。

### 2.2 ingress-controller（四路径路由前提）

**验收命令**：

```bash
kubectl get deployment ingress-nginx-controller -n ingress-nginx   # READY 1/1（名称随安装而异）
kubectl get svc ingress-nginx-controller -n ingress-nginx -o wide  # EXTERNAL-IP 就绪
kubectl get ingressclass                                    # 存在默认 IngressClass（nginx）
```

**预期判读**：controller Deployment Ready、Service 有 EXTERNAL-IP/LoadBalancer 地址、存在被引用 IngressClass → PASS。§3 路径实测 404/502 时优先回查此项。

**常见失败处置**：Deployment 未就绪看事件（`kubectl describe deploy -n ingress-nginx ingress-nginx-controller`）；EXTERNAL-IP 空 → 检查云 LB 配额/注解；若集群用非 ingress-nginx 控制器，§3 的 annotations 期望值与本文不同，需按实际控制器语法对照。

### 2.3 RWX 存储类（多副本上传卷前提）

> 技术注意：**StorageClass 资源本身没有 accessModes 字段**（accessModes 是 PVC/卷的属性），因此"遍历 sc 取 accessModes"无法实现。正确验收是：① 指定候选存储类并确认 Chart 已用其创建并绑定了 RWX 的 PVC；② 或用探测 PVC 实证 RWX 能力。`scripts/verify-cluster.ps1` 采用同款逻辑（集群内已存在 Bound 且 accessModes 含 ReadWriteMany 的 PVC 即判 PASS，可 `-RWXName` 指定期望存储类名）。

**验收命令**：

```bash
# ① Chart PVC 是否已用 RWX 绑定（生产落地即此形态）：
kubectl get pvc admin-scaffold-upload -n admin-scaffold -o jsonpath='{.status.phase} {.spec.accessModes[0]} {.spec.storageClassName}'
# 期望：Bound ReadWriteMany <storage.className 或默认类>

# ② 列出候选存储类并探查绑定到 RWX 卷的 PVC：
kubectl get sc
kubectl get pvc -A | grep -i bound   # 结合 kubectl get pvc -A -o yaml 核对 accessModes: [ReadWriteMany]

# ③ （候选类未知时）探测 PVC 实证：
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: PersistentVolumeClaim
metadata: { name: rwx-probe, namespace: admin-scaffold }
spec:
  accessModes: ["ReadWriteMany"]
  storageClassName: <候选SC>       # 如 efs-sc / nfs-client / cephfs
  resources: { requests: { storage: 1Gi } }
EOF
kubectl get pvc rwx-probe -n admin-scaffold -w    # 期望 Bound
kubectl delete pvc rwx-probe -n admin-scaffold    # 验证后删除
```

**预期判读**：`admin-scaffold-upload` PVC 为 `Bound` 且 accessModes 含 `ReadWriteMany`，或探测 PVC Bound → PASS。PVC Pending → RWX 存储类不可用 → FAIL。

**常见失败处置**：Chart 侧确认 `--set storage.className=<RWX类>`（默认空=默认存储类，若默认类非 RWX 则多副本后端共享上传卷会失败，见 `k8s/README.md`「上传持久化」）；云厂商按 EFS/NFS/CephFS 创建动态供给 SC；探测 PVC 长 Pending 时 `kubectl describe pvc rwx-probe` 看卷供给事件。

### 2.4 cert-manager（TLS 自动签发前提，`ingress.tls.enabled=true` 时必配）

**验收命令**：

```bash
kubectl get deploy -n cert-manager cert-manager cert-manager-cainjector cert-manager-webhook  # 均 READY 1/1
kubectl get clusterissuer                      # 存在且 READY=True（或 Issuer，ns 内）
kubectl get issuer,clusterissuer -A           # 列出全部，找 READY=True 的 issuer 名
```

**预期判读**：三个 Deployment Ready **且**至少一个 ClusterIssuer/Issuer `READY=True` → PASS。issuer 存在但非 Ready → FAIL（§3 证书签不出来）。issuer 名需与 Chart `ingress.tls.clusterIssuer` 填的值一致（`values.yaml` 示例 `letsencrypt-prod / ca-issuer`）。

**常见失败处置**：Webhook Deployment 未 Ready 通常因 webhook 证书未就绪/防火墙拦 443 回调，查看 `cert-manager-webhook` Pod 日志；ClusterIssuer 非 Ready 用 `kubectl describe clusterissuer <名>` 看 Conditions.Message（如 Let's Encrypt 注册/ACME 网络问题）；测试环境可用自签 CA issuer（`ca-issuer`），生产用 `letsencrypt-prod` 并确认域名可公网解析。

---

## 3. Ingress 路由与安全（B1 / B2 / B3）

> 期望配置值来自 Chart：`ingress.host`（默认 `admin.example.com`，生产改真实域名）、`ingress.tls.enabled=true`、`ingress.tls.clusterIssuer=<issuer 名>`、`ingress.forceHttps=true`、`ingress.proxyBodySize="20m"`（默认恒注入，与后端 multipart 上限 20MB 对齐，见 `k8s/helm/admin-scaffold/values.yaml`）。模板渲染逻辑在 `k8s/helm/admin-scaffold/templates/all.yaml` 的 Ingress 段（annotation 与 tls 块为条件渲染）。

### 3.1 TLS 与强制 HTTPS（B1）

**验收命令**：

```bash
kubectl get ingress admin-scaffold-ingress -n admin-scaffold -o yaml
# 期望 annotations 至少含：
#   cert-manager.io/cluster-issuer: "<已填的 issuer 名>"
#   nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
#   nginx.ingress.kubernetes.io/proxy-body-size: "20m"
kubectl get certificate -n admin-scaffold        # READY True（ingress-shim 自动创建，名形如 admin-scaffold-tls-xxx）
kubectl get secret admin-scaffold-tls -n admin-scaffold -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -dates -subject
```

**端到端实测**（域名 DNS 已指向 LB 后）：

```bash
# ① TLS 握手 + 前端可达：
curl -sS -o /dev/null -w '%{http_code}\n' https://<admin.example.com>/          # 期望 200
# ② API 路由抵达后端（未带 token 时 401/403 属正常——证明已到后端鉴权层而非 502/404/HTML 回退）：
curl -sS -o /dev/null -w '%{http_code}\n' https://<admin.example.com>/api/system/notice/ticket
# 期望 401/403（未鉴权），不是 502/404/200+index.html
curl -sSI http://<admin.example.com>/ | head -1                                   # 期望 301/308 → Location: https://...
```

**预期判读**：Ingress 含 issuer 注解与 `tls` 块（secretName `admin-scaffold-tls`）；Certificate READY True、Secret 内证书在有效期内；HTTPS 访问成功、HTTP 被强制跳转 HTTPS → PASS。任一缺失 → FAIL。

**常见失败处置**：issuer 注解缺失 → Chart 未开 `ingress.tls.enabled` 或渲染时 `clusterIssuer` 为空；证书长期 Pending → 查 Certificate/Order/Challenge 事件（`kubectl describe certificate -n admin-scaffold`），ACME HTTP-01 需 80 端口可达与域名解析指向本集群 LB。

### 3.2 四条路径路由 + SSE 关缓冲（B2）

**Ingress 规则核验**：

```bash
kubectl describe ingress admin-scaffold-ingress -n admin-scaffold
# 期望 Rules 段（host = <真实域名>）：
#   /api     → admin-scaffold-backend:8080   （Prefix）
#   /files   → admin-scaffold-backend:8080   （Prefix）
#   /uploads → admin-scaffold-backend:8080   （Prefix）
#   /ws      → admin-scaffold-backend:8080   （Prefix）
#   /        → admin-scaffold-frontend:8080  （Prefix）
kubectl get ingress admin-scaffold-ingress -n admin-scaffold -o jsonpath='{range .spec.rules[*].http.paths[*]}{.path} -> {.backend.service.name}:{.backend.service.port.number}{"\n"}{end}'
```

**预期判读**：四条业务路径全部指向 backend、`/` 指向 frontend，且与 `templates/all.yaml` 路由一致 → PASS（路径缺失/错指 frontend 即 FAIL，历史教训：/ws 缺失会落 SPA 回退返回 index.html 导致 WS 握手失败、/uploads 缺失致存量文件 403，见 all.yaml 内注释）。

**SSE 关缓冲 —— 重要现状说明**：仓库 Chart **不渲染 SSE 专属注解**（`all.yaml` Ingress 段只按需输出 cluster-issuer / force-ssl-redirect / proxy-body-size 三个 annotation，**无** `nginx.ingress.kubernetes.io/proxy-buffering: "off"`、无 `proxy-read-timeout`）。因此本项**不能只查注解，必须以功能实测为准**：

```bash
# 功能性核验（后端站内通知 SSE：先取一次性 ticket，再开流；60s 内完成）：
TOKEN=$(curl -s https://<admin.example.com>/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"<验收账号>","password":"<口令>"}' | jq -r '.data.accessToken')
TICKET=$(curl -s https://<admin.example.com>/api/system/notice/ticket -H "Authorization: Bearer $TOKEN" | jq -r '.data')
timeout 8 curl -sN --no-buffer "https://<admin.example.com>/api/system/notice/stream?ticket=$TICKET" \
  -H 'Accept: text/event-stream' -H 'Cache-Control: no-cache'
# 期望：首条 event 在数秒内到达（含 : hb 心跳注释行）并持续输出，而非全部积压到请求结束才一次性返回
```

> 另一处 SSE 通道：AI 任务流 `GET /api/ai/tasks/{id}/stream?ticket=...`（先打 `/api/ai/tasks/{id}/ticket`）。后端 SSE 端点见 `backend/src/main/java/com/example/admin/module/system/SystemNoticeController.java`（`/api/system/notice/ticket`、`/api/system/notice/stream`）与 `NoticeSseService`（15s 心跳）。
> **判读与处置**：若 SSE 事件逐条实时到达 → PASS。若观察到事件被缓冲成批/首个事件显著延迟才返回 → 需补关缓冲：① 在 Ingress 增加 `nginx.ingress.kubernetes.io/proxy-buffering: "off"`（长连接可再配 `nginx.ingress.kubernetes.io/proxy-read-timeout: "300"`）；② 因 Chart 无对应参数，需自行扩展模板（如 values 加 `ingress.sseBuffering: false`）或经 `--set ingress.annotations.*` 类覆盖——此为仓库已知待补缺口，落地时须记录在复检备注。另请同时确认 ingress-nginx controller 的 ConfigMap 未全局开启 `proxy-buffering`。相关背景：`docs/release-review-2025.md` P1-F2（实时推送通道部署断点）。

**WS 路径（/ws）核验**：管理端站内消息 WebSocket 由后端 handler 映射在 `/ws/messages`（`backend/src/main/java/com/example/admin/config/WebSocketConfig.java`），Ingress 的 `/ws` Prefix 规则命中它直达 backend（历史缺此路径时 WS 落 SPA 回退返回 index.html、握手必然失败）。核验：浏览器登录后开 F12 确认 `wss://<host>/ws/messages?ticket=...` 握手 101 成功且能收到消息；命令行可用 `websocat wss://<host>/ws/messages?ticket=<ticket>`（需先取一次性 ticket）验证升级成功。

**常见失败处置**：路径实测 404 且返回 index.html 内容 → 落入 frontend SPA 回退，查 Ingress 路径与 Service 名是否被手工改动/漂移（ArgoCD selfHeal 会还原）；502/超时 → 回查 §2.2 与 backend Deployment/Service 端点。

### 3.3 上传链路 >1MB 实测（B3）

**验收命令**：

```bash
# 上传文件管理页或直接 POST 一个 2MB 文件到上传接口（如 /api/system/file/upload，带 Bearer token）：
dd if=/dev/urandom of=/tmp/big.bin bs=1M count=2
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://<admin.example.com>/api/system/file/upload \
  -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/big.bin"
# 期望 200（业务成功响应），而非 413 Request Entity Too Large
kubectl get ingress admin-scaffold-ingress -n admin-scaffold -o jsonpath='{.metadata.annotations.nginx\.ingress\.kubernetes\.io/proxy-body-size}'
# 期望 20m
```

**预期判读**：>1MB（如 2MB）上传成功、Ingress annotation `proxy-body-size: "20m"` → PASS。413 → FAIL（网关默认 1MB 限制未放开；历史 P1-D3：后端允许 20MB 但网关 1MB 时 >1MB 必 413）。

**常见失败处置**：413 时确认 Chart `ingress.proxyBodySize` 未被置 0（置 0 模板不输出注解）且集群内 Ingress 未被外部覆盖；同时核对容器侧无 `client_max_body_size` 更小限制（镜像内 `frontend/nginx.conf` 不代理 /api，本链路主要由 Ingress 决定；本地 docker 栈则看 `docker/nginx.conf`）。

---

## 4. 可观测性与告警触达（B4 / B5）

> 现状事实：仓库**不提供 Prometheus/Alertmanager 服务端清单**（只提供配置 `k8s/monitoring/prometheus.yml`、`prometheus-rules.yml`、`alertmanager.yml`），Grafana 提供清单（`k8s/monitoring/grafana/grafana.yaml`，monitoring 命名空间）。生产 Prometheus/Alertmanager 需预先部署（如 `kube-prometheus-stack`），部署说明见 `docs/deploy/README.md` §3 与 `k8s/monitoring/grafana/README.md`。**部署方式不同则 Pod/Service 名不同**，下列命令按通用命名，请以实际部署为准。

### 4.1 Prometheus 抓取两个目标 200（B4）

抓取目标（`k8s/monitoring/prometheus.yml`，跨命名空间 FQDN，release/命名空间不同须同步修改）：

- `admin-backend` job：`admin-scaffold-backend.admin-scaffold.svc.cluster.local:8080`，`metrics_path=/actuator/prometheus`
- `ai-service` job：`admin-scaffold-ai.admin-scaffold.svc.cluster.local:8000`，`metrics_path=/metrics`（根路径；**绝不能再配成 `/api/v1/metrics`**——批次5·R4-1.51 修正，配错会抓取 404 → `up{job="ai-service"}==0` → AIServiceDown 上线即持续误报）

**验收命令**（本地端口转发 Prometheus 后查询）：

```bash
kubectl -n monitoring port-forward svc/prometheus 9090:9090 &   # service 名按实际部署
curl -s 'http://localhost:9090/api/v1/targets?state=active' | jq '.data.activeTargets[] | {job: .labels.job, health, lastError}'
# 期望：admin-backend 与 ai-service 两行 health == "up"，lastError 为空
curl -s 'http://localhost:9090/api/v1/query?query=up' | jq '.data.result[] | {job: .metric.job, value: .value[1]}'
# 期望：up{job="admin-backend"}==1 且 up{job="ai-service"}==1
curl -s 'http://localhost:9090/api/v1/alerts' | jq '.data.alerts[] | select(.labels.alertname=="AIServiceDown")'
# 期望：无 firing（无 AIServiceDown 误报）
```

**预期判读**：两 job `up` 且无 firing 的 AIServiceDown → PASS。某目标 `down`/`lastError` 非空 → FAIL：FQDN 与 release/命名空间不符、Service 端口不符、NetworkPolicy 未放行监控抓取（`docs/release-review-2025.md` P2 提及 NetworkPolicy 未放行 Prometheus 抓取 /metrics 的风险）。

**常见失败处置**：抓取失败先 `kubectl get endpoints admin-scaffold-backend -n admin-scaffold` 确认有端点；再从 Prometheus Pod 内 `wget http://admin-scaffold-backend.admin-scaffold.svc.cluster.local:8080/actuator/prometheus` 复现；检查 NetworkPolicy（`k8s/helm/admin-scaffold/templates/networkpolicy.yaml`）是否放行来自 monitoring 的抓取。

### 4.2 Alertmanager 真实接收器验证（B5）

> **现状事实（必须先处理）**：`k8s/monitoring/alertmanager.yml` 的 `receivers.default` 下 webhook_configs（钉钉/企微）与 email_configs **全部处于注释占位状态**（无真实 URL/收件人），全局 smtp 段同样注释——**未填真实接收人前，任何"测试告警"都只到空接收器，等于静默漏报**。填法按该文件头部注释：取消注释并填钉钉/企微机器人 URL，或填 email_configs 的 `to` + smtp 全局段；也可用环境变量注入（文件内给出了容器化示例）。判定 B5 = 真实接收器已配置 **且** 至少一次发送成功有证据。

**方法一：直接向接收器发测试消息**（不经 Prometheus，先验证接收链路本身）：

```bash
# 钉钉/企微机器人：直接 POST 一条测试消息到已填的真实 webhook URL（替换为实际 URL）：
curl -sS -H 'Content-Type: application/json' \
  -d '{"msgtype":"text","text":{"content":"【上线验收】Alertmanager 接收器连通性测试 <time>"}}' \
  https://oapi.dingtalk.com/robot/send?access_token=<真实TOKEN>
# 期望 {"errcode":0,...} 且群内收到消息；群机器人安全设置（关键字/加签）不符会 errcode 310000

# 邮件：用真实 SMTP 发一封测试邮件到收件人（或后续 amtool notify 观察 SMTP 日志）

# amtool（更贴近真实告警链路：按告警 labels 走 Alertmanager 路由到接收器；flags 以本机 amtool --help 为准）：
amtool alertmanager notify --alertmanager.url=http://<alertmanager-svc>:9093 --webhook.url=<真实钉钉URL> \
  "alertname=ConnectivityTest" "severity=critical" "summary=AM接收器连通性测试"
```

**方法二：触发/注入一条真实告警，观察收敛路由送达**（验证全链路：规则 → AlertManager → 接收器）：

> 注意：Chart 的 backend/ai 均带 HPA（minReplicas=2）且 ArgoCD 开启 selfHeal——**临时 `scale deployment --replicas=0` 会被 HPA/自愈很快拉回**，等不到规则 `for: 2m`，不推荐用宕机规则做日常验收（仅在变更窗口可配合暂停同步/HPA 时用）。推荐用**临时注入一条必然触发的规则**（只动 Prometheus 规则配置，不动业务负载）：

1. 确认 alertmanager.yml 已填真实接收器并生效（`kill -HUP` 或按部署方式 `amtool reload` / rollout restart）。
2. 临时注入必然告警规则（追加到 `k8s/monitoring/prometheus-rules.yml` 挂载的规则文件，或直接改 Prometheus 侧 rules ConfigMap 后 reload）：

```yaml
- alert: AMConnectivityProbe
  expr: vector(1)          # 恒真，下一轮评估即触发
  labels: { severity: warning }
  annotations:
    summary: "接收器连通性验收：本告警为临时注入，确认收到后请删除本规则"
```

3. 等 `evaluation_interval`（默认 15s）+ `for`(未设即立即) + `group_wait: 30s` 后，观察接收渠道；`amtool alert query --alertmanager.url=...` 应能看到该告警进入 Alertmanager。
4. **验收后必须删除该临时规则并 reload**，避免残留恒真告警。

**备选真实规则**（供变更窗口演练，规则名与 `for` 见 `k8s/monitoring/prometheus-rules.yml`）：`AdminBackendDown`/`AIServiceDown`（`up==0` 持续 2m）或 `BackendHighErrorRate`（5xx>5% 持续 5m，可对某只读接口制造安全 5xx）。触发时先与发布窗口协调（必要时临时停用 ArgoCD auto-sync 与对应 HPA），结束后恢复并观察恢复通知（send_resolved）。

**预期判读**：接收渠道实收测试消息/临时规则告警/真实告警 → PASS；仅配置占位（receivers 仍注释）→ 直接 No。

**常见失败处置**：消息未达 → ① 确认 alertmanager.yml 已填且重载；② `amtool alertmanager notify` 报错看 URL 可达性；③ 群机器人安全设置与消息格式不匹配（钉钉关键字/加签、企微 key）；④ Alertmanager 日志（`kubectl logs <alertmanager-pod> -n monitoring`）看 notification 报错；⑤ 邮件场景检查 SMTP 端口/认证/`smtp_require_tls`。

---

## 5. 备份链路（B6）

### 5.1 仓库当前备份机制形态（按已核实事实如实说明）

- **仓库内没有任何 K8s CronJob / 定时备份的 Helm 模板或 yaml**（全仓 grep：无 `kind: CronJob`、无 `schedule:` 备份定义；`k8s/helm/admin-scaffold/templates/all.yaml` 亦无 CronJob 段）。**定时备份不在 Chart 内。**
- 现状形态 = **脚本 + 宿主机计划任务**（`docs/runbook.md`「定时与异地」）：`scripts/backup.ps1` 产出 `backups/<db>_<时间戳>/`（MySQL mysqldump 逻辑全量 `--single-transaction --routines --triggers` + 可选 Redis RDB `redis-cli --rdb` + 可选 MinIO `mc mirror`，口令经 `MYSQL_PWD`/`REDISCLI_AUTH` 注入），由 Windows 计划任务或 Linux cron 每日触发；异地 = 定期 `mc mirror /backups s3/...` 同步对象存储或把 `OutputDir` 指向挂载的对象存储目录。
- **PITR**：mysqldump 只给"最近一次全量"恢复点；要求 PITR 需开 MySQL binlog 定期归档增量，或直接用云 RDS 自动备份。
- **目标基线（runbook）**：全量每日 RPO≤24h，+ binlog 增量 RPO≤分钟级；RTO 以最近一次 backup-drill 实测为准；演练每季度一次。
- **结论表述**：若生产 MySQL 在集群外（云 RDS / 自建虚机，仓库默认 `config.dbHost=mysql` 指向外部），沿用"宿主机计划任务 + 异地对象存储"即仓库给出的落地方式；**若要改成集群内定时备份（K8s CronJob），仓库无现成模板，需自行上 CronJob**——backup.ps1 是 PowerShell 脚本（依赖 mysqldump.exe/redis-cli/mc 路径），进 CronJob 需自备含 pwsh + mysql 客户端（或改写为纯 mysqldump 命令）的镜像，属落地团队自建项，验收记录表应注明采用哪种形态。

### 5.2 落地点检步骤

**① 定时执行已配置且在跑**：

```bash
# Linux cron 形态（runbook 示例：每日 02:00 全量）：
crontab -l | grep -i backup
# Windows 计划任务形态：
schtasks /query /fo LIST | findstr /i backup
# 记录：任务路径 / 触发时间 / 是否近期成功（查任务上次运行结果与备份日志）
ls -lt <OutputDir>            # 期望最近 ≤24h 存在 admin_scaffold_<时间戳>/ 目录
```

**② 异地副本存在且新鲜**（对象存储或异地目录）：

```bash
mc ls s3/<bucket>/admin-scaffold-backups/   # 期望与本地最新备份时间戳一致（或由同步任务保证）
# 若 OutputDir 直接挂对象存储目录，则① 与② 合一
```

**③ backup-drill 演练**（每季度至少一次，独立库恢复验证，`scripts/backup-drill.ps1` 头部见用法）：

```powershell
# 演练只覆盖 MySQL 恢复链路（Redis RDB 需停机、MinIO 需 mc 环境，不纳入自动演练）：
& scripts/backup-drill.ps1 -DbHost <host> -DbUser <user> -DbPassword '<口令>' -DbName admin_scaffold
# 期望输出：Backup drill PASS: restored N tables from <file>（表数 N>0；restore.ps1 默认校验表数量 + sys_user 抽查）
# 注意：drill 会在所连同一 MySQL 实例上建/删 <db>_drill 临时库并做一次全量 dump+恢复（额外 IO），
# 建议低峰执行、不触碰业务库本身；口令尽量避免明文（runbook 示例从 /run/secrets 等受管来源读取）。
```

**④ RPO/RTO 记录表**（按本次演练与备份产物实测填写，写入 §1.3 记录表并附证据）：

| 项 | 目标（runbook 基线） | 本次实测 | 证据 |
| --- | --- | --- | --- |
| RPO（数据丢失上限） | 全量每日 ≤24h；开 binlog/云 RDS 后 ≤分钟级 |  | 备份产物时间戳与演练库数据新鲜度 |
| RTO（恢复时长上限） | 以最近一次 drill 实测为准 |  | backup-drill 起止时间（含恢复 + 校验） |
| 最近一次 drill 日期 | 每季度 ≤1 次 |  | 演练输出/日志 |
| 异地副本新鲜度 | 与最近全量一致 |  | mc ls 时间戳 |

### 5.3 判读与常见失败处置

- 计划任务缺失/上次运行失败（备份日志有 mysqldump 报错、目录为空）→ No；先按 `scripts/backup.ps1` 参数（`-MySqlBin`/`-RedisCli`/`-McBin` 等默认路径可能与本机不符）手动跑通一次并修正计划任务。
- drill 失败（表数 0 或抛异常）→ No；多半是备份文件损坏或 restore 目标库问题，先人工 `restore.ps1` 到独立库复现。
- 同机同盘备份在磁盘故障时与主库同毁——异地副本项为硬性必过项（runbook 明确）。
- 备份含业务数据与文件对象，归档/传输按敏感数据处理（runbook）。

---

## 6. GitOps 与密钥（B7）

> 事实：`gitops/argocd/application.yaml` 定义 Application `admin-scaffold`（namespace `argocd`，dest namespace `admin-scaffold`，repoURL 目前是 **TODO 占位 `https://github.com/your-org/admin-scaffold.git`，落地前必须替换为真实私有仓库**）；`values-prod.yaml` 中 `secret.*` **必须保持为空**（密钥由部署时注入），空值由 Chart 顶部 fail 校验兜底 → **密钥未注入时 ArgoCD 同步必然失败**（这正是 fail-fast 设计）；可复制密钥注入示例在 `gitops/argocd/eso-example/README.md`（方案 A：ESO SecretStore + ExternalSecret；方案 B：手工 Secret），设置 `secret.existingSecret=<Secret 名>` 后 Chart 跳过空值校验并引用外部 Secret。

### 6.1 ArgoCD Application 同步健康

**验收命令**：

```bash
kubectl get application admin-scaffold -n argocd -o wide
# 期望 SYNC STATUS=Synced、HEALTH STATUS=Healthy
kubectl get application admin-scaffold -n argocd -o jsonpath='{.status.health.status} {.status.sync.status}{"\n"}'
kubectl get application admin-scaffold -n argocd -o jsonpath='{.status.operationState.message}{"\n"}'   # 无报错
kubectl get pods -n admin-scaffold    # 四个 Deployment 均 Ready（backend/ai/frontend/website 各 2 副本 + HPA/PDB）
argocd app get admin-scaffold         # （装了 argocd CLI 时）或 argocd app sync admin-scaffold
```

**预期判读**：`Synced` + `Healthy`，业务 Pod 就绪 → PASS。`OutOfSync`（自愈前瞬时状态可接受，持续即问题）/`Degraded`/`ComparisonError` → FAIL。

**常见失败处置**：`ComparisonError/Empty values` 报密钥空 → 见 6.2 注入密钥；镜像/配置错误看 Application 事件与 `argocd app logs`；禁止手工 `kubectl apply` 修生产（会被 prune/selfHeal 回滚，`gitops/README.md`）。

### 6.2 ESO SecretStore 示例落地与密钥 fail-fast 验证

**落地步骤**（完整可复制配置在 [`gitops/argocd/eso-example/README.md`](../../gitops/argocd/eso-example/README.md)）：

1. 前置：集群已装 External Secrets Operator（ESO）。
2. 创建 `SecretStore`（示例为 AWS Secrets Manager，provider 按实际云调整），声明密钥来源。
3. 创建 `ExternalSecret`（名 `admin-scaffold-secret`，与 Chart `secret.existingSecret` 一致），把云侧 5 个键（`DB_PASSWORD/JWT_SECRET/TOTP_ENCRYPTION_KEY/AUTH_TOKEN/MCP_AUTH_TOKEN`）映射为同名 K8s Secret 键。
4. values 覆盖设 `secret.existingSecret=admin-scaffold-secret`（ArgoCD 经 `helm.parameters` 或 valueFiles 注入；或手工 Secret 方案 `kubectl create secret generic admin-scaffold-secret --from-literal=...`）。

**验收命令**：

```bash
kubectl get secretstore,externalsecret -n admin-scaffold            # 存在且无报错
kubectl get secret admin-scaffold-secret -n admin-scaffold          # 存在
kubectl get secret admin-scaffold-secret -n admin-scaffold -o jsonpath='{range $k,$v := .data}{$k}{"\n"}{end}'
# 期望输出 5 个键名：DB_PASSWORD JWT_SECRET TOTP_ENCRYPTION_KEY AUTH_TOKEN MCP_AUTH_TOKEN（不打印值）
kubectl get externalsecret admin-scaffold-secret -n admin-scaffold -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}{"\n"}'
# 期望 True

# fail-fast 验证（本机/CI 环境即可，无需连生产）：
helm template ./k8s/helm/admin-scaffold --namespace admin-scaffold          # 不带任何 secret → 期望失败，报 all.yaml 顶部 fail 文案
helm template ./k8s/helm/admin-scaffold --namespace admin-scaffold \
  --set secret.existingSecret=admin-scaffold-secret                          # 期望渲染成功且不创建内部 Secret
# 或在 ArgoCD 侧确认：密钥注入前同步失败（ComparisonError），注入后 Synced——失败即保护，属预期行为
```

**预期判读**：SecretStore/ExternalSecret Ready、Secret 含 5 键、fail-fast 双向验证符合预期（无密钥渲染失败 / 有 existingSecret 渲染通过）→ PASS。ExternalSecret 非 Ready 或键缺失 → FAIL（四个"一致性"键任一不一致会出现 401/403/连库失败，见 eso-example 轮换说明）。

**常见失败处置**：ExternalSecret Ready=False 看 `.status.conditions[].message`（云凭据 IRSA/static 认证、region、远端 key 名）；键名不一致导致应用启动失败时先核对键名再 rollout restart（轮换流程见 `docs/runbook.md`「密钥轮换流程」与 eso-example 轮换说明）。

---

## 7. 执行顺序建议

1. 先跑 `scripts/verify-cluster.ps1 -KubeContext <prod-context>` 过四件套与集群底座（§2 对应项）。
2. 再按 §3（Ingress）→ §4（可观测/告警）→ §6（GitOps/密钥）→ §5（备份，演练需低峰窗口）顺序验收。
3. 逐行填 §1.3 记录表；任一 No/待确认 → 整改后**整组复检**（复检要重跑预检脚本确认无回归）。

## 8. 需人工在集群上确认的悬空项（本手册不可自动判定的部分）

以下各项依赖集群实际状态/人工填真，文档无法替你判定，须在记录表标注"待人工确认"直至有证据：

1. **四件套存在性之外的细节**：ingress-nginx controller 的实际命名空间/Deployment 名与 Service EXTERNAL-IP；`kubectl top nodes` 是否真返回数据（受 metrics-server 证书/内网 CA 影响）。
2. **RWX 存储类名**：Chart `storage.className` 实际注入值；候选 SC 的 RWX 能力（本文 §2.3 用探测 PVC 实证，仓库无法预知云厂商 SC 名）。
3. **issuer 名称与状态**：`ingress.tls.clusterIssuer` 应填的 ClusterIssuer 名，及该 issuer READY 状态（仓库只给示例名）。
4. **Chart 无 SSE 关缓冲注解**：SSE 功能实测是否通过（§3.2）；若需补注解，属于仓库模板待补缺口，需记录采用哪种补法（扩展模板/额外覆盖）及实施人。
5. **真实域名与 DNS**：`ingress.host`/`corsAllowedOriginPatterns`/官网域名在生产是否已替换 `admin.example.com` 等占位；证书签发依赖域名解析指向 LB。
6. **Alertmanager 真实接收人**：钉钉/企微机器人 URL 或邮件收件人 + SMTP（`alertmanager.yml` 现为注释占位）——填哪个渠道、由谁维护，未填前 B5 恒为 No。
7. **监控栈实际部署形态**：Prometheus/Alertmanager 的 Pod/Service 名与部署方式（仓库不提供其清单）；`prometheus.yml` targets FQDN 是否与 release/命名空间一致（不一致须改）。
8. **ArgoCD repoURL**：`gitops/argocd/application.yaml` 仍是 TODO 占位仓库，须替换为真实私有仓库；ArgoCD 实例/命名空间是否就绪。
9. **备份形态选择与真值**：采用"宿主机计划任务"还是"集群内自建 CronJob"；异地对象存储端点/bucket；binlog 是否开启（PITR 前提）；最近 drill 时间与 RPO/RTO 实测记录。
10. **ESO 云侧凭据与远端密钥**：SecretStore 的 provider 认证（IRSA/static 等）与云侧密钥条目是否已就绪（仓库示例为 AWS Secrets Manager）。
