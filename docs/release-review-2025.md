# 智能管理平台 · 上线前综合评审报告（生产级落地版）

> 评审日期：2025 年（快照基线）
> 评审方式：7 个维度并行深度评审（后端 / 前端 / AI / DevOps / 安全 / 测试质量 / 文档体验），全部基于实际代码、配置、部署清单与 CI 实现逐文件核查。
> 结论速览：**整体就绪度约 3.8/5——架构与工程纪律已达企业级，但存在 1 个 P0（默认管理员口令）与约 20 个 P1 级问题，必须分批整改后才能达到"可对外投产、可被企业二次开发"的标准。**

---

## 一、评审结论总览

| 维度 | 就绪度 | 一句话结论 |
| --- | --- | --- |
| 后端 Java/Spring Boot | 4 / 5 | 架构分层与可靠性纵深（outbox/分布式锁/多租户/数据权限）完成度远超一般脚手架；但依赖有已知 CVE、逻辑删除+唯一键冲突、数据权限 fail-open、Quartz 反射无白名单 |
| 前端 Vue3 管理端+官网 | 4 / 5 | TS strict 零 any、请求层/契约测试/i18n 全对齐，工程纪律生产级；但非幂等 POST 自动重试、SSE/WS 部署断点、refresh token 落 localStorage、nginx 缺安全头 |
| AI 服务 FastAPI | 3 / 5 | 队列与 SSRF 防护设计教科书级；但解析依赖从未声明（生产必挂）、调度瞬时失败即永久停摆、CPU 密集阻塞事件循环、Helm 无 LLM 注入通道、无限流 |
| DevOps 部署运维 | 3 / 5 | Helm/K8s/CI/GitOps 骨架完整；但 AI 指标端点配置漂移（必误报宕机）、Ingress 无 TLS、上传 1MB 网关限制冲突、备份无定时/PITR/异地、Alertmanager 占位符 |
| 全栈安全 | 3.5 / 5 | 认证闭环/SSRF 纵深/密钥 fail-fast 上游水平；但默认管理员口令 P0、病毒扫描生产默认关闭、DB 明文链路、AI 向量隔离依赖调用方 |
| 测试与质量门禁 | 3.5 / 5 | 门禁真实生效（JaCoCo/契约测试/故障注入）；但报表 SQL 与工作流引擎零测试、AI 无覆盖率门禁、gitleaks 不阻断、性能无基线 |
| 文档与二次开发体验 | 3.5 / 5 | 架构/部署/运维文档质量高且与代码一致；但前端零开发文档、CRUD 生成器产物违背 i18n/数据权限规约、无回滚指南、一键启动名不副实 |

**总体评分：约 3.8/5（可进预生产评审，暂不可直接对外投产）。**

### 核心判断

1. **这是一个真实经历过缺陷复盘的高质量脚手架**：R4-1.28 到 R4-1.46 的批次注释、fail-fast 密钥体系、多租户白名单、报表 SQL 守卫、SSE/WS 连接上限等，都是认真做过的痕迹，绝非 demo。
2. **但"脚手架"与"可投产产品"之间仍有明确缺口**，集中在三类：
   - **安全语义缺口**（默认口令、病毒扫描默认关、数据权限 fail-open、DB 明文）；
   - **部署链路缺口**（监控配置漂移、TLS、1MB 上传限制、备份自动化、实时通道三处配置不一致）；
   - **可靠性缺口**（AI 调度停摆、CPU 阻塞事件循环、逻辑删除+唯一键、Quartz 反射）。
3. **整改顺序建议**：先安全与部署阻断项（P0/P1），再可靠性与测试补强（P1/P2），最后文档与品牌化（P2/P3）。下文给出分 8 批的落地计划，每批附可直接投喂 AI 编码助手的提示词。

---

## 二、P0 级问题（上线阻断，必须最先修复）

### [P0-1] 默认管理员口令可直接登录
- **位置**：`backend/src/main/resources/db/migration/V1__init.sql:129`（种子 admin 的 bcrypt 哈希即 `admin123` 的知名哈希）
- **问题**：种子管理员使用公开默认口令，且**无首次登录强制改密**、无密码过期策略（`SystemUserService.java:54` 初始密码 `admin123`）。
- **影响**：上线即被接管，属最高危项。
- **建议**：① 种子数据只保留占位，启动时检测默认哈希 → 强制进入初始化改密流程；② 或由环境变量注入随机初始口令；③ 增加首登改密 + 密码过期（90 天）策略。

### [P0-2] 报表 SQL 执行引擎与 Warm-Flow 工作流引擎零测试（质量维度）
- **位置**：`backend/src/test/java/.../report/` 无 ReportService 测试；WarmFlowWorkflowService / WarmFlowPermissionHandler / WarmFlowLegacyMigrator 无测试
- **问题**：两个高风险第三方引擎封装没有任何测试，守卫（ReportSqlGuard）之外的执行路径完全裸奔。
- **影响**：报表 SQL 语法错误、结果集映射缺陷、工作流审批/驳回/租户隔离回归无法被 CI 拦截。
- **建议**：补 ReportService + ReportSqlGuard 真实 MySQL 集成测试（恶意 SQL 拒绝、分页、大结果集、超时），补 WarmFlow 委托/驳回/租户隔离单测 + 至少 1 条真实 MySQL 流程流转 IT。

### [P0-3] AI 服务无覆盖率门禁且全部基于 fakeredis（质量维度）
- **位置**：`ai-service/pyproject.toml`（无 pytest-cov/`--cov-fail-under`）；`tests/conftest.py`、`test_app_smoke.py`（`Redis = FakeRedis`）
- **问题**：测试可随删随过；FakeRedis 与真实 Redis 的语义差异（Lua/过期/流）会掩盖生产问题。
- **建议**：加 `--cov --cov-fail-under=60` 门禁；至少 1 个真实 Redis（docker 起容器）的 TaskManager 集成测试。

---

## 三、P1 级问题（投产前必须处理，按域分组）

### 后端（5 项）

| # | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| P1-B1 | Spring Boot 3.3.5（Spring 6.1.14）已 EOL 且含已知 CVE（multipart/range DoS）；SnakeYAML 2.2 有 CVE-2024-57649 | `pom.xml:10` | 升级 Boot 3.4/3.5.x（或至少 3.3.13 + snakeyaml 2.4+），全量 mvn verify 回归 |
| P1-B2 | 逻辑删除与唯一键冲突：删除后同名数据永远无法重建 | `V33__...unique_key.sql:7-8`、`SysUserDO.java:36-37` 等 | 物理删除或 `(tenant_id, code, deleted 时间戳)` 唯一键；新增表禁止该组合 |
| P1-B3 | 数据权限 SQL 改写失败时静默放行（fail-open）+ 仅支持 PlainSelect（子查询/UNION 不注入） | `DataScopeInnerInterceptor.java:72-96` | 改 fail-closed（涉及受控表改写失败即拒绝）；TablesNamesFinder 全树收集 |
| P1-B4 | Quartz 任务反射调用无白名单（任意 Bean 无参方法可被触发，如 scheduler.shutdown） | `JobInvokeUtil.java:13-22`、`MonitorJobService.java:167-181` | invokeTarget 正则白名单 + 可调用方法注册表 + cron 语法校验与事务回滚 |
| P1-B5 | JwtAuthenticationFilter 每请求 2+ 次数据库查询（角色+用户行未走缓存） | `JwtAuthenticationFilter.java:51-57` | 角色随 perms 进 Redis 短缓存（TTL 抖动 + after-commit 失效） |

### 前端（4 项）

| # | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| P1-F1 | 非幂等 POST 在网络错误/超时时被自动重试（重复提交事故） | `frontend/src/utils/request.ts:121-128` | 仅对 GET/HEAD/OPTIONS 自动重试；写操作不重试或引入幂等键 |
| P1-F2 | 实时推送通道部署断点：Ingress 无 SSE 注解、`/ws` 未代理、WS 无重连 | `all.yaml:442-466`、`docker/nginx.conf`、`BasicLayout.vue:530-558` | Ingress 加 proxy-buffering off / proxy-read-timeout 300 + `/ws` 路径；docker nginx 加 /ws upgrade 代理；前端 WS 补指数退避重连 |
| P1-F3 | refresh token 与 access token 同存 localStorage（XSS 即长期会话接管） | `frontend/src/utils/auth.ts:1-2,28-33` | refresh token 改 httpOnly+Secure+SameSite cookie，前端仅内存持有 access token |
| P1-F4 | 管理端与官网 nginx 均无安全响应头（点击劫持/MIME 嗅探） | `frontend/nginx.conf`、`website/nginx.conf` | X-Frame-Options / X-Content-Type-Options / Referrer-Policy / Permissions-Policy |

### AI 服务（6 项）

| # | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| P1-A1 | pdf/docx/xlsx 解析与 Tesseract OCR 依赖从未声明——生产必挂且 OCR 静默回退 Mock（"无声数据造假"） | `pyproject.toml:6-14`、`Dockerfile`、`ocr/registry.py:17-23` | pyproject 增加 parsing extra（pypdf/python-docx/openpyxl），Dockerfile `uv sync --extra parsing` + apt 装 tesseract；回退 Mock 时告警 + fail_if_unavailable 配置 |
| P1-A2 | 调度器单次瞬时失败即永久停摆（due 条目已移除且不重排） | `scheduler/service.py:254-271`、`:218-252` | _process_due 先推进 run_count/next_ts 再 create_task，失败按指数退避重排；register 时校验 biz_type；409 幂等 |
| P1-A3 | RUNNING 遗留任务仅启动时回收一次，滚动发布后任务卡死近 1 小时 | `tasks/manager.py:368-403`、`:156-164` | worker 循环内周期调 recover_stale_tasks（60s）；close() 前把在途 RUNNING 标记 requeue |
| P1-A4 | 同步 CPU 密集工作在事件循环上执行（100 页 PDF 解析冻结全部请求 → 探针重启级联） | `services/doc_parse.py`、`rag/pipeline.py:52`、`vectors/store.py:69-77`、`ml/cluster.py` | 统一 `asyncio.to_thread`；大文档加 50MB 上限与解析超时 |
| P1-A5 | 出站 LLM 请求不脱敏输入 PII，且无任何限流/输入尺寸上限 | `services/llm_chat.py:31-38`、`schemas/ai.py` | 出站前 mask()；message.max_chars；Redis 滑动窗口限流 429；params ≤5MB |
| P1-A6 | Helm 无 LLM_PROVIDERS/WORKER_COUNT 注入通道，K8s 生产只能跑 Mock（向量维度错配无报错） | `templates/all.yaml:25-45`、`values.yaml` | values 增加 ai.llmProviders/workerCount/ocrProvider，LLM key 走独立 Secret |

### DevOps（6 项）

| # | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| P1-D1 | AI 指标抓取端点配置与代码漂移（prometheus.yml 用 /api/v1/metrics，代码在 /metrics）→ AIServiceDown 上线即持续误报 | `k8s/monitoring/prometheus.yml:33-36` vs `main.py:100` | metrics_path 改回 /metrics，统一修正 3 处文档 |
| P1-D2 | Ingress 无 TLS / 无 cert-manager / 未强制 HTTPS | `all.yaml:442-483`、`values.yaml:139` | values 增加 ingress.tls.enabled/clusterIssuer/forceHttps，模板渲染 tls 块 + force-ssl-redirect |
| P1-D3 | 上传 20MB 与网关默认 1MB 请求体限制冲突（>1MB 上传必 413） | `application.yml:25-27` vs `all.yaml`/`docker/nginx.conf` | Ingress 加 proxy-body-size: 20m 注解；nginx 加 client_max_body_size 20m |
| P1-D4 | 备份无 PITR、无定时、无异地，灾备实际不可用 | `scripts/backup.ps1:31-41`、`backup-drill.ps1` | binlog 归档或云 RDS 自动备份；计划任务定时 + 异地同步；明确 RPO/RTO |
| P1-D5 | Alertmanager 接收器为占位符，且 DB/Redis 规则引用未启用的 exporter job → 静默漏报 | `alertmanager.yml:14-23`、`prometheus-rules.yml:74-90` | 配置真实接收器（钉钉/企微/邮件/PagerDuty）；启用对应 exporter 或删除规则 |
| P1-D6 | ArgoCD 密钥注入与 fail-fast 空值耦合，values-prod 恒空 → 同步永远失败且无示例 | `gitops/argocd/values-prod.yaml:19-24` | 提供 ESO（ExternalSecret）或 helm.parameters 完整示例；或 Chart 支持 secret.existingSecret |

### 安全（3 项，与上重叠的 B3/F3 不重复列出）

| # | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| P1-S1 | 病毒扫描生产默认关闭（FILE_SCAN_ENABLED:false → NoopFileVirusScanner），且扩展名含 zip/rar/7z、doc/xls（宏/压缩炸弹面） | `application.yml:138`、`application-prod.yml` | 生产强制 scan.enabled=true + fail-open=false；压缩包限层数/体积；Office 宏剥离 |
| P1-S2 | 生产 DB 连接 useSSL=false&allowPublicKeyRetrieval=true（数据链路明文） | `application-prod.yml:17` | DB TLS 或至少同 VPC + 强 NetworkPolicy Egress 兜底 |
| P1-S3 | AI 向量隔离依赖调用方构造租户化 namespace（/vectors/* 未强制校验） | `routes.py`（/vectors 端点） | namespace 由服务端派生并强制 `tenant:{id}:` 前缀 |

---

## 四、P2 级问题（应当整改，影响质量/效率/运维）

**后端**：导入用户单事务+逐行 2 次 DB 往返（SystemUserService.importUsers）；数据权限每请求全表载入部门树+万级 IN 列表（DataScopeHelper）；监控大屏 stats() 7+ 条无时间边界全表 COUNT；日志类表无保留期/归档；业务异常一律 HTTP 200；日志打印完整 SQL/异常详情。
**前端**：exportUsers/downloadFile 绕过统一请求管道（无 401 刷新、JSON 错误被当文件下载）；登录页 redirect 未校验+预填 admin；官网"预约演示"表单是前端 no-op 且占位内容（400-800-0015/hello@example.com/©2026/虚构定价）随上线流出；官网 SEO 不足且 build 无类型检查、CI 无 lint；index.html/sw.js 未设 no-cache（PWA 发版拿旧壳）；WS 无重连与生命周期处理。
**AI**：liveness/readiness 共用 /health（Redis 抖动即重启循环）；worker 空转忙轮询 20ms + 并发随副本线性放大无全局上限；重试退避固定 0.5s 无抖动（重试风暴）；生产代码残留 force_fail/delay_seconds 演示后门；NetworkPolicy 未放行 Prometheus 抓取 /metrics；_promote_due 忽略 zrem 返回值。
**DevOps**：CI 未对构建产物做镜像扫描与 helm lint；HPA 依赖 metrics-server 未声明；Chart 无 imagePullSecrets、默认 latest tag；NetworkPolicy 前端/website 实际放开任意来源；备份脚本命令行明文口令+编码 BOM 风险；监控规则按不存在的 service 标签聚合；多环境交付链路缺失。
**安全**：CI 门禁不阻断发布（docker-build 与 security-scan 并行）；改密/踢下线不撤销存量 access token（120min）；数据权限仅 PlainSelect 顶层表（子查询绕过）；logback 无全局 PII/密钥掩码（仅注解级）；NetworkPolicy 无 Egress 限制。
**质量**：gitleaks 默认不阻断（fail: true 未设）；前端覆盖率门槛 32% 偏低且路由守卫/权限 store 零测试；E2E 仅 2 条链路且断言脆弱；load-test 吞错无基线无 SLO；后端无 spotbugs/checkstyle/pmd。
**文档**：README 声称含"演示材料"实际不存在；openapi.md 仅 16 行且全库 Controller 零 @Tag/@Operation 注解、openapi.json 未入库、CI 无契约新鲜度门禁；数据库无 ER 图/列级说明；无 CONTRIBUTING/FAQ/回滚指南/LICENSE；生成器硬编码 com.example 且 .pyc 入库；版本号多处不一致（README Node 20 vs CI 22）。

---

## 五、P3 级问题（建议优化，摘录）

- 依赖版本：hutool 5.8.32→5.8.39+、EasyExcel 3.3.4/POI 4.1.2→EasyExcel 4.0.3、minio 8.5.12→8.5.17+、MCP SDK 0.11.0 跟进。
- 包名 `com.example` 未品牌化 + 硬编码 `admin` 角色编码短路数据权限。
- 基础镜像 floating tag 无 digest 锁定；backend/Dockerfile 硬编码 `admin-backend-0.0.1-SNAPSHOT.jar`；milvus.yaml 的 `--set milvus.enabled=true` 与 Chart 实际键 `config.milvusEnabled` 不符；docs/architecture.md 称"上报 Tempo"实为 Zipkin（文档漂移）。
- 后端无 startupProbe；Dockerfile 无 HEALTHCHECK；`terminationGracePeriodSeconds` 挂在 storage 下语义耦合。
- 前端：App.vue 错误边界不记录错误（无监控上报）；ProTable 为薄封装无强类型契约；LoginView 登录卡 <360px 溢出；入口 chunk 1.02MB 可拆分；website PWA 与 SEO 冲突。
- AI：回调每次新建 httpx client；build_registry 不校验 provider 配置；task_ttl_seconds 未使用；/chat/stream 中途故障无错误事件；task usage 恒 None 无成本核算。

---

## 六、分 8 批评改落地计划（每批附可直接投喂的提示词）

> 每批一个提交、一个主题、可独立验收。提示词可直接复制给 AI 编码助手（Claude/Cursor/通义灵码等）执行；**每批执行后必须跑对应门禁**（后端 `mvn verify`、前端 `pnpm lint && pnpm test && pnpm build`、AI `uv run pytest`）。

### 批次 1：安全阻断与认证链路（上线前必须，预计 3-5 天）

**整改点**：① 默认管理员口令处置（占位种子 + 启动检测强制初始化改密 + 首登改密 + 密码过期策略）；② 前端非幂等 POST 不再自动重试；③ refresh token 迁移 httpOnly cookie；④ 登录 redirect 白名单校验 + 移除预填 admin。

> **提示词**：「请对 backend 与 frontend 执行"安全阻断"整改：1) 修改 V1 种子或启动初始化逻辑，使默认 admin 口令不再可直接登录——启动时检测到默认 bcrypt 哈希即强制进入初始化改密流程，新增首登改密与密码过期（90 天）策略，补充对应单测；2) 修改 frontend/src/utils/request.ts 的错误拦截器，仅对 GET/HEAD/OPTIONS 在网络错误/超时时重试一次，其他方法直接 reject；3) 将 utils/auth.ts 的 refresh token 改为 httpOnly cookie 方案（后端 /auth/refresh 配合 Set-Cookie，前端仅内存持有 accessToken，刷新后静默续期），同步调整 api/auth.ts 与 401 刷新逻辑；4) LoginView.vue 校验 redirect 必须以单斜杠开头且不以 // 开头，移除 username 预填。为每项补/改测试并保证 vue-tsc 与 mvn verify 通过。」

### 批次 2：依赖升级与认证性能（上线前必须）

**整改点**：① Spring Boot 3.3.5 → 3.5.x（含 snakeyaml 2.4+）；② JwtAuthenticationFilter 角色/用户行并入 Redis 短缓存；③ Quartz invokeTarget 白名单 + cron 校验 + 事务回滚；④ 依赖版本刷新（hutool/EasyExcel/minio/MCP SDK）。

> **提示词**：「请对 backend 执行"依赖与认证链路"整改：1) 将 spring-boot-starter-parent 升级至 3.5.x 并覆盖 snakeyaml 至 2.4+，运行 mvn verify 全量回归（含 Testcontainers IT 与 JaCoCo 门槛），修复所有编译/测试问题；2) 为 JobInvokeUtil 增加 invokeTarget 格式白名单（正则校验 beanName.method 仅含字母数字下划线）与可调用方法注册表，未登记即抛业务异常；3) 重构 MonitorJobService.create/update 为 @Transactional，先用 CronExpression.isValidExpression 校验 cron 再入库，scheduleJob 抛异常时回滚；4) 在 TokenService 中为 (userId→roles) 增加与 perms 同模式的 Redis 缓存（TTL 30min±抖动、事务提交后失效），JwtAuthenticationFilter 复用缓存减少每请求 DB 查询；5) 顺带升级 hutool→5.8.39+、EasyExcel→4.0.3、minio→8.5.17+。补充对应单测并保证 JaCoCo 门槛不降。」

### 批次 3：AI 服务生产化（上线前必须，预计 3-5 天）

**整改点**：① 依赖声明 + Dockerfile（parsing extra + tesseract）；② 调度器可靠性（失败重排 + biz_type 校验 + 409 幂等）；③ 周期租约回收 + 优雅停机 requeue；④ CPU 密集 to_thread + 文档上限；⑤ 出站 PII 脱敏 + 限流/尺寸上限；⑥ /livez 与 /readyz 拆分；⑦ Helm 增加 AI 配置注入通道。

> **提示词**：「请对 ai-service 与 Helm 执行"生产化"整改：1) pyproject.toml 增加 [project.optional-dependencies] parsing=["pypdf","python-docx","openpyxl"]，Dockerfile 的 uv sync 增加 --extra parsing 并 apt-get install tesseract-ocr，OCR 探测失败回退 Mock 时 logger.warning 告警并新增 settings.ocr_fail_fast；2) scheduler/service.py 的 _process_due 改为先推进 run_count 并计算 next_ts 再 create_task，失败按指数退避重排 due，register 时校验 biz_type 存在，create_task 抛 409 按幂等成功处理；3) tasks/manager.py 增加每 60s 周期调 recover_stale_tasks，close() 前把在途 RUNNING 任务标记 requeue；4) 将 doc_parse/chunker/向量余弦/chat 内 PII detect 全部用 asyncio.to_thread 包装，doc_parse 增加 50MB 上限与解析超时；5) LlmChatService 出站前对 user 消息执行 pii.mask，schemas 增加 content 长度上限，create_task 校验 params ≤5MB，实现 Redis 滑动窗口限流（429）；6) main.py 新增 /livez（恒 200）与 /readyz（Redis 探活），Helm 探针 liveness 指向 /livez、readiness 指向 /readyz；7) values.yaml 增加 ai.llmProviders/workerCount/ocrProvider，Secret 增加 aiLlmApiKeys，模板为 ai-service 注入 LLM_PROVIDERS 等环境变量。为每项补 pytest 用例，uv run pytest 全绿。」

### 批次 4：数据一致性与权限边界（上线前必须）

**整改点**：① 全库排查"逻辑删除+唯一键"表；② importUsers 分批事务；③ DataScopeInnerInterceptor 改 fail-closed + 子查询全树收集；④ DataScopeHelper 单 SQL + 子查询 + 短缓存。

> **提示词**：「请对 backend 执行"数据一致性"整改：1) 扫描 sys_user/sys_role/sys_post/sys_dept/ai_service_config 等所有同时存在 deleted 列与业务编码唯一键的表，将唯一键改为 (tenant_id, code, deleted) 并把 deleted 语义改为删除时间戳（0=未删），调整实体与 delete 逻辑，新增"删除→重建同名"回归测试；2) 重构 SystemUserService.importUsers：先批量预检查重，再按 500 行分批事务插入，收集失败行明细返回，避免整批回滚与长事务；3) DataScopeInnerInterceptor.beforeQuery 中，当 DataScopeContext 激活且查询涉及受控表时，若 SQL 改写失败或语句类型非 PlainSelect（含派生表/UNION），改为抛出拒绝异常而非静默放行，用 TablesNamesFinder 全树遍历收集所有表；4) DataScopeHelper.deptAndChildIds 改为单条 ancestors LIKE 查询，allowedUserIds 的 IN 列表改写为子查询，结果增加 60 秒短缓存；5) 补充派生表绕过与 fail-closed 的单元测试。」

### 批次 5：部署链路生产化（上线前必须）

**整改点**：① AI metrics_path 修正 + 文档统一；② Ingress TLS + proxy-body-size + force-ssl-redirect；③ /ws 代理（K8s + docker nginx）；④ nginx 安全响应头 + index.html/sw.js no-cache；⑤ 前端 WS 指数退避重连。

> **提示词**：「请按以下清单整改部署配置（只改配置与前端连通性代码）：1) 将 k8s/monitoring/prometheus.yml 中 ai-service job 的 metrics_path 改为 /metrics，并同步修正 k8s/README.md、grafana/README.md、docs/deploy/README.md 中所有 /api/v1/metrics 表述；2) 在 k8s/helm/admin-scaffold/values.yaml 增加 ingress.tls.enabled/clusterIssuer/proxyBodySize/forceHttps 参数，templates/all.yaml 的 Ingress 渲染 tls 块与 cert-manager/force-ssl-redirect/proxy-body-size 注解，并新增 /ws 前缀路径指向 backend service；3) docker/nginx.conf 增加 client_max_body_size 20m 与 location /ws/ 的 WebSocket 反代（Upgrade/Connection 头）；4) 为 frontend/nginx.conf 与 website/nginx.conf 增加 X-Frame-Options/X-Content-Type-Options/Referrer-Policy/Permissions-Policy 及 location = /index.html、= /sw.js 的 no-cache；5) 在 layout/BasicLayout.vue 为 messageSocket 实现与 noticeStream 一致的指数退避重连（含 disposed/maxRetry 守卫），onUnmounted 显式 close。」

### 批次 6：监控、备份与 GitOps 落地（上线前必须，可与批次 5 合并执行）

**整改点**：① Alertmanager 真实接收器 + 启用/修正 exporter 规则；② 备份定时 + 异地 + PITR 文档；③ gitops 提供 ESO/helm.parameters 可落地示例；④ metrics-server 前置条件文档。

> **提示词**：「请完成监控与运维落地：1) 将 alertmanager.yml 的占位邮箱替换为可配置的真实接收器（支持 webhook/email 两种，参数化），prometheus-rules.yml 中 Redis/MySQL 规则按实际 exporter job 名对齐并注明启用条件，补充 PVC 磁盘水位、outbox 死信、AI 死信队列规则；2) scripts/backup.ps1 改用 MYSQL_PWD 环境变量传口令、mysqldump 用 --result-file 落盘，新增 docs/runbook.md 章节说明 binlog 归档 + Windows 计划任务/Linux cron 定时 + 异地对象存储同步 + RPO/RTO 目标；3) 在 gitops/ 提供 ExternalSecret + SecretStore 模板（或 ArgoCD helm.parameters 密钥注入完整示例），使 GitOps 链路可独立跑通；4) 在 k8s/README.md 与 docs/deploy/README.md 的"集群前置条件"补充 metrics-server、ingress-controller、RWX 存储类、cert-manager 的安装命令。」

### 批次 7：测试与质量门禁补强（上线后第一周）

**整改点**：① ReportService + ReportSqlGuard 真实 MySQL IT；② WarmFlow 工作流 Service 单测 + 1 条流程 IT；③ AI 覆盖率门禁 + 真实 Redis 集成测试；④ gitleaks fail: true；⑤ E2E 增 3 条链路（权限 403/上传/报表）；⑥ 前端 router/permission store 测试与分目录门槛；⑦ 后端接入 spotbugs（先 report 后 enforce）。

> **提示词**：「请按以下清单补强测试门禁：1) 为 ReportService.execute 编写基于 Testcontainers MySQL 的集成测试：覆盖 SQL 注入拒绝、超时中断、10 万行结果集分页与内存上限断言，失败时阻断 mvn verify；2) 为 WarmFlowWorkflowService 补充委托/驳回/撤回/租户隔离的单测，并加 1 条真实 MySQL 的流程流转 IT；3) ai-service 的 pyproject.toml 增加 pytest-cov 与 --cov-fail-under=60，新增一个用真实 Redis（docker 容器）的 TaskManager 集成测试文件并接入 CI；4) ci.yml 的 gitleaks 步骤显式设 fail: true 并验证含假密钥的 PR 确实红；5) e2e 增加"低权限账号访问受限页面 403 + 文件上传 + 报表执行"3 条链路，断言改用 role/locator 而非易变文本；6) 前端为 router/index.ts 与 stores/permission.ts 补充测试并将覆盖率门槛按目录拆分（router/stores 强制 ≥60%）；7) backend/pom.xml 接入 spotbugs-maven-plugin（先 report 后 enforce 模式）。」

### 批次 8：文档、品牌化与二次开发体验（上线后第一周）

**整改点**：① 前端开发文档（frontend/README + 组件库使用说明 + 新增业务模块指引）；② CRUD 生成器规约对齐（i18n + 数据权限）；③ 全部 Controller 补 @Tag/@Operation 注解 + openapi.json 入库 + CI 契约门禁；④ runbook 扩容（告警处置对照表/扩容/回滚/密钥轮换）；⑤ 包名品牌化 com.example → 企业域名；⑥ 占位内容真实化（官网电话/邮箱/版权/定价）；⑦ 一键启动脚本补前置检查。

> **提示词**：「请执行"文档与品牌化"整改：1) 为 frontend/ 编写 README.md：目录约定（api/components/composables/stores/views）、ProTable/ProSearchForm/ModalForm/useTableQuery 用法、菜单→路由→页面新增链路、zh-CN/en-US 语言包维护流程，并给出新增一个业务模块的完整示例；2) 改造 scripts/crud-gen：生成页文案改走 i18n key，Service 增加可选 @DataScope 与写路径归属校验，规格文件增加 datascope 配置项，包名参数化不再硬编码 com.example；3) 为全部后端 Controller 补齐 @Tag/@Operation 注解（可写脚本批量生成草稿后人工审），将 docs/api/openapi.json 入库并在 CI 增加"openapi diff 失败即构建失败"门禁；4) runbook.md 增加：告警规则→处置动作对照表、水平扩容步骤、密钥轮换流程（JWT/TOTP/DB 密码分步换）、Flyway 失败与版本回滚补偿策略、helm diff/rollback 操作；5) 将 backend 全部包名 com.example.admin 重命名为企业域名（如 com.yourcompany.admin），同步 pom groupId 与 crud-gen；6) 将 website/src/App.vue 中 400-800-0015、hello@example.com、© 2026 与虚构定价改为可配置常量并替换为正式内容；7) scripts/start-dev.ps1 增加环境前置检查（pnpm/node/python/mysql/redis 是否存在并给出安装指引）。」

---

## 七、上线 Go/No-Go 检查清单

> 以下全部为 **Yes** 才允许对外投产；有一项 No 即 No-Go。

### A. 安全（必须全绿）
- [ ] 默认管理员口令已处置（无法用 admin/admin123 登录，首登强制改密）
- [ ] 生产 profile 病毒扫描已开启（FILE_SCAN_ENABLED=true，ClamAV 可用且 fail-open=false 有评审结论）
- [ ] 生产 DB 连接不再 useSSL=false（或已用 VPC + NetworkPolicy Egress 兜底并有评审记录）
- [ ] 前端 refresh token 已移出 localStorage（httpOnly cookie 或等价方案）
- [ ] 数据权限拦截器已 fail-closed（含子查询场景）
- [ ] Quartz invokeTarget 白名单已生效
- [ ] 生产密钥全部经 K8s Secret/ESO/Vault 注入，无明文默认密钥可上线路径（JWT/TOTP/MCP/AI AUTH/DB）

### B. 部署（必须全绿）
- [ ] Helm Ingress 已启用 TLS（cert-manager + force-ssl-redirect）
- [ ] /api、/files、/uploads、/ws 四条路径在 Ingress 均正确路由；SSE 代理已关缓冲
- [ ] 上传链路实测 >1MB 文件成功（proxy-body-size/client_max_body_size 已配）
- [ ] Prometheus 抓取 backend /actuator/prometheus 与 ai /metrics 均 200，无 AIServiceDown 误报
- [ ] Alertmanager 真实接收器已验证发送成功
- [ ] 备份已定时执行 + 异地副本 + 至少一次 backup-drill 演练，RPO/RTO 有记录
- [ ] ArgoCD/GitOps 链路按文档可独立跑通（密钥注入有可复制示例）
- [ ] metrics-server / ingress-controller / RWX 存储类 / cert-manager 四件套已确认存在

### C. 可靠性（必须全绿）
- [ ] `mvn verify` 全绿（含 Testcontainers IT 与 JaCoCo 门槛），Boot 已升级无已知高危 CVE
- [ ] `pnpm lint && pnpm test && pnpm build`（vue-tsc）全绿
- [ ] `uv run pytest` 全绿且有覆盖率门禁
- [ ] E2E（admin 登录→看板、官网表单）在 CI 通过
- [ ] AI 服务：pdf/docx/xlsx 解析在镜像内可用（非 mock）；调度器瞬时故障后可自愈；滚动发布后任务不卡死
- [ ] 报表 SQL 执行与工作流引擎已有集成测试
- [ ] 逻辑删除+唯一键冲突已修复（删除后可重建同名）

### D. 内容与合规（必须全绿）
- [ ] 官网占位内容（电话/邮箱/版权/定价）已真实化
- [ ] 官网"预约演示"表单已接后端（非前端 no-op）
- [ ] 管理端 index.html title/description、PWA 图标已品牌化
- [ ] 依赖扫描（trivy HIGH/CRITICAL）为 0 或已评审接受；LICENSE 明确

---

## 八、给企业的二次开发注意事项（浓缩版）

1. **新增业务表三条铁律**：必须带 `tenant_id` 且加入 `MybatisPlusConfig.TENANT_TABLES` 白名单；禁止"逻辑删除+业务编码唯一键"组合；需要行级数据权限时在 `sys_data_permission` 配置并在 Service 加 `@DataScope`——自写 SQL/复杂查询不要指望拦截器。
2. **前端权限只是 UX 不是安全边界**：`v-permission` 与动态菜单只做展示层过滤，新增接口必须由后端鉴权兜底（@PreAuthorize + URL 注册表）。
3. **三处前端代理配置职责不同，极易改漏**：镜像内 `frontend/nginx.conf`（生产，不代理 /api）、`docker/nginx.conf`（本地栈，代理 /api 且 SSE 关缓冲）、Helm Ingress（生产路由）。新增任何实时通道/文件路径必须三处同步评估。
4. **AI "Mock 默认"是双刃剑**：未配置 LLM_PROVIDERS 时全链路跑假数据；部署流水线应增加自检（`llm_default_provider=mock` 且非 dev 时告警/拒绝启动）。向量维度 mock(16) 与真实 provider(384/1536) 禁止混跑。
5. **多副本 ≠ 高可用**：Quartz 已集群化、发件箱原子抢占、SSE 走 Redis 广播，但滚动更新会切断既有 SSE 长连接（前端需自动重连）；Redis 是单点（队列/向量/调度/死信全在 Redis），务必托管/哨兵并加内存预警。
6. **定时任务两条路要分清**：Quartz 管理任务（invokeTarget 只允许登记过的 bean 方法）；`@Scheduled` 多副本必须套 `ScheduledTaskLock`。
7. **Flyway 纪律**：已发布迁移文件一个字符都别改；新改动一律 V63+ 增量迁移；生产 baseline-on-migrate=false；上线前在 test 环境全量演练并保留数据库快照。
8. **CI 门槛是保护不是负担**：分支保护需配 required status checks（backend/frontend/ai-service/security-scan/e2e/docker-build）；JaCoCo 门槛应随覆盖率提升持续上调（40%→60%+）。
9. **CRUD 生成器产物必须过评审再用**：当前产物缺数据权限与 i18n，直接合入会污染基线（批次 8 完成后可推广）。
10. **品牌化要趁早**：`com.example` 包名、`admin-backend` artifactId、管理端标题/图标/官网联系方式，在第一批业务代码落地前完成全局重命名，成本最低。

---

*本报告为只读评审产物，未修改任何项目文件。各批次整改提示词可直接复制使用；执行每批后请按第七节检查清单逐项验收。*
