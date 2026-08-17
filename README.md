# 智能管理平台

面向企业生产管理的全栈平台：管理员使用 **后台管理系统**，外部客户与访客访问 **智能管理平台** 官网。仓库包含 Java 业务后端、Python AI 服务、Vue3 管理端与 Vue3 官网，可作为企业生产管理和 Vibe Coding 二次开发的基线。

## 核心能力

- 认证与安全：验证码、登录失败锁定、登录限流、TOTP 2FA（密钥加密存储）、API 全局限流、JWT + Redis Token、Token 刷新、个人资料编辑、第三方 OAuth2 登录（微信/GitHub/Gitee）、SSO 授权码服务
- RBAC 权限：用户、角色、菜单、按钮权限、数据权限（AOP 注解 + SQL 拦截器统一注入）
- 企业基础：部门、岗位、数据字典、参数配置、通知公告、文件管理
- 统一文件服务：本地磁盘/MinIO 统一 SPI、上传校验、文件分类、SHA256、ClamAV 病毒扫描、存储配额、签名内容端点、鉴权下载
- 消息中心：系统消息、审批待办、全员广播、未读角标、已读管理、顶部铃铛聚合消息与公告、WebSocket + SSE 实时推送
- 工程能力：Quartz JDBC 持久化定时任务、Excel 导入导出、OpenAPI
- 数据可靠性：全表审计字段、审计日志、敏感字段脱敏、`version` 乐观锁、逻辑删除、Flyway V1-V62
- 智能互联：MCP 双端接入，平台 SSE 端点对外暴露只读工具（用户/设备/统计），客户端可配置消费外部 MCP Server
- 工程规范：阿里巴巴 Java 开发手册分批整改、统一错误码、日志脱敏、核心 Service 单测、Manager 分层
- 实时协作：SSE Ticket + WebSocket 双通道、未读角标、AI 任务实时状态、服务器/SQL 告警分级与 Webhook
- 完整工作流：基于 Warm-Flow 1.8.9 的流程定义、条件/并行节点、发布/取消发布、表单数据、待办任务、通过/驳回/撤回/转办、审批日志，存量 `sys_process_def` 启动时自动迁移兼容
- 多租户：租户配额、租户管理员、角色/部门/字典/参数/业务表 `tenant_id` 隔离、数据权限联动
- 可观测性：服务器监控、SQL 监控、操作日志、审计日志、Prometheus、`requestId/traceId`
- 前端体验：全量中英文国际化、暗黑模式、移动端响应式、PWA 离线缓存
- 官网转化：智能管理平台提供产品展示、解决方案、定价与预约演示
- 交付质量：GitHub Actions 覆盖后端/前端/AI/官网构建测试、前端 ESLint、后端 JaCoCo 覆盖率门槛、CodeQL，冒烟与压测脚本

## 扩展进度

| 批次 | 模块 | 状态 |
| --- | --- | --- |
| 1 | 统一文件服务 | 已完成 |
| 2 | 消息中心 | 已完成 |
| 3 | 工作流升级 Warm-Flow | 已完成 |
| 4 | 多租户深化 | 已完成 |
| 5 | AI 能力增强（模型网关 / Prompt / RAG + Milvus） | 已完成 |
| 6 | 报表与大屏 | 已完成 |
| 7 | 分布式调度 | 已完成 |
| 8 | 认证扩展 | 已完成 |
| 9 | 字段级审计 | 已完成 |
| 10 | 设备管理 | 已完成 |
| 11 | 消息多渠道 | 已完成 |
| 12 | MCP | 已完成 |
| 13 | 可观测性增强 | 已完成 |
| 14 | 前端组件沉淀 | 已完成 |

## 前后端对接优化进度

| 批次 | 范围 | 状态 |
| --- | --- | --- |
| P0 | 消息中心待办接入 Warm-Flow、铃铛聚合消息与公告、公告/消息详情直达 | 已完成 |
| P1 | 工作流双轨收敛、实例/待办组合查询、文件访问 token、统一通知聚合接口 | 已完成 |
| P2 | 类型契约、租户管理员候选、工作流详情、前端统一状态与国际化 | 已完成 |

## 整改批次进度

按 `R4-1.xx` 编号逐批整改，每批一个提交，批 1 起自 R4-1.28。

| 批次 | 编号 | 范围 | 状态 |
| --- | --- | --- | --- |
| 1 | R4-1.28 | OAuth 凭据 AES-GCM 加密存储、MinIO 生产凭据守卫 | 已完成 |
| 2 | R4-1.29 | 租户隔离与认证安全加固（工作流多租户、OAuth 绑定限流/失败锁定） | 已完成 |
| 3 | R4-1.30 | 并发可靠性加固（发件箱原子抢占、锁看门狗续期、AI 调度/消费幂等） | 已完成 |
| 4 | R4-1.31 | HTTP 语义标准化、缓存失效覆盖、AI 同步调用 LLM 重试 | 已完成 |
| 5 | R4-1.32 | 前端类型契约与测试门禁（request 泛型化、拦截器/契约测试、覆盖门槛上调） | 已完成 |
| 6 | R4-1.33 | 前端体验打磨（菜单去重、暗黑主题持久化、跨标签页登出、列表请求取消、keep-alive） | 已完成 |
| 7 | R4-1.34 | AI 服务打磨（LLM 连接池、任务超时裁剪、未知 provider 400、PII 输出强制） | 已完成 |
| 8 | R4-1.35 | 后端打磨（缓存 TTL 防雪崩、日志脱敏/打码回显、@Valid 补齐、审计数据权限） | 已完成 |
| 9 | R4-1.36 | 扩展性与工程化（菜单 id 动态化、JaCoCo 门禁上调、CRUD 生成器、IT 无 Docker 跳过） | 已完成 |
| 10 | R4-1.37 | 安全与数据权限（渠道敏感字段加密落库、报表执行参数校验、业务表数据权限扩展） | 已完成 |
| 11 | R4-1.38 | 数据权限单条路径补漏、渠道发送 PII 加密、发送类操作日志脱敏 | 已完成 |
| 12 | R4-1.39 | 安全漏洞优先修复（文件下载 IDOR、分页全局封顶、登录锁定租户化、写路径数据权限） | 已完成 |
| 13 | R4-1.40 | 安全加固（MCP SSRF、AI 状态机竞态、AI/MCP 凭据加密、审批头像契约、密码复杂度） | 已完成 |
| 14 | R4-1.41 | 环境与日志脱敏（nginx 补 /files 反代、MCP authToken 入敏感键清单） | 已完成 |
| 15 | R4-1.42 | 环境与审计对齐（K8s Ingress 补 /uploads、上传审计统一、MultipartFile 元信息化） | 已完成 |
| 16 | R4-1.43 | 文件访问令牌一致性收口（/uploads 归属校验、令牌现取统一、死字段清理） | 已完成 |
| 17 | R4-1.44 | 安全纵深（AI 回调有界读、Refresh 原子消费、AI baseUrl SSRF、预签名归属）+ 前端取消接通 + onMounted 收敛 + 文档对齐 | 已完成 |
| 18 | R4-1.45 | 工程与部署对齐（CORS 兜底收紧、Helm 内部主机名按 release 推导、CI website 改 pnpm、Playwright E2E 进 CI） | 已完成 |
| 19 | R4-1.46 | 部署文档与 E2E 门禁对齐（CORS 生产必配说明、e2e typecheck 门禁） | 进行中 |

## 技术栈

| 端 | 技术 |
| --- | --- |
| 管理端 | Vue 3、TypeScript、Vite 7、Ant Design Vue 4、Pinia、Vue Router、ECharts、vue-i18n、TanStack Query、Vitest |
| 官网 | Vue 3、Vite 7、lucide-vue-next |
| Java 后端 | Spring Boot 3.3、Spring Security 6、MyBatis-Plus、Flyway、Quartz、Redis、JWT、EasyExcel、MinIO、Micrometer、Knife4j |
| AI 服务 | FastAPI、Redis、httpx、pytest、Prometheus Client |
| 基础设施 | MySQL 8、Redis 7、Kubernetes、Helm |

## 仓库结构

```text
frontend/    后台管理系统（Vue3 管理端）
website/     智能管理平台官网
backend/     Spring Boot 后端与 Flyway 脚本（当前 V1-V62）
ai-service/  FastAPI AI 服务
docs/        接口、数据库、部署、演示材料
k8s/         Kubernetes 清单与 Helm Chart
scripts/     启动、停止、冒烟、备份、恢复、压测、OpenAPI 脚本
```

## 本地启动

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

Windows 一键启动：

```powershell
scripts/start-dev.ps1
```

手动启动：

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
cd ai-service && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev
cd website && pnpm install && pnpm dev --port 5174
```

> application.yml 默认 `prod` profile（未注入密钥即启动失败，杜绝静默携带公开 dev 密钥上线）；本地开发必须显式注入 `SPRING_PROFILES_ACTIVE=dev`。Windows 一键启动脚本 [scripts/start-dev.ps1](scripts/start-dev.ps1) 已自动处理。

访问入口：

- 后台管理系统：http://localhost:5173 ，默认管理员：`admin / admin123`
- 智能管理平台官网：http://localhost:5174
- 后端接口文档：http://localhost:8080/doc.html

MySQL/Redis 启动后，后端通过 Flyway 自动完成建表与基础数据初始化。

## Kubernetes 与 Helm

使用 Helm Chart（K8s 部署的唯一来源，含 HPA、PDB 与 Ingress；原生清单已移除避免漂移）：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace
```

生产环境请通过 `--set secret.*` 显式注入密钥（留空时模板 fail-fast），详见 [k8s/README.md](k8s/README.md)。

详见 [k8s/README.md](k8s/README.md)。

## 测试与运维

```powershell
scripts/start-dev.ps1
scripts/stop-dev.ps1
scripts/smoke.ps1
scripts/backup.ps1
scripts/restore.ps1
scripts/load-test.ps1
scripts/load-test-multi.ps1
scripts/fetch-openapi.ps1
```

CI 流水线见 `.github/workflows/ci.yml`。
GitOps 交付见 [gitops/README.md](gitops/README.md)。

## 文档入口

- 接口文档：`docs/api/`
- 数据库设计：`docs/database/`
- 部署教程：`docs/deploy/`
- 架构说明：`docs/architecture.md`
- 架构设计规约：`docs/architecture-conventions.md`
- 运维手册：`docs/runbook.md`
- 阿里规约合规说明：`docs/alibaba-compliance.md`

