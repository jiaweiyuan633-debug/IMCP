# 智能管理平台（admin-scaffold）

面向企业生产管理的全栈开发脚手架：包含 **Java 业务后端**、**Python AI 服务**、**Vue3 管理端** 与 **Vue3 官网**。仓库以"开箱即用 + 可长期二次开发"为基线，企业可在其之上快速落地自己的管理业务，而非从零搭建基础设施。

本仓库使用 [Apache-2.0](./LICENSE) 许可。

## 定位与适用对象

- **管理员侧**：企业后台管理系统（用户/角色/菜单/数据权限/工作流/文件/消息/报表等）。
- **客户侧**：对外官网（产品展示与线索收集，联系方式与线索端点为配置驱动，未配置时运行于演示模式）。
- **二次开发**：通过 `scripts/crud-gen/` 快速生成标准 CRUD 模块骨架，复杂业务在生成代码上扩展。
- **部署形态**：单机开发（docker compose）与生产（Kubernetes + Helm + GitOps）两套交付。

## 内置能力

**认证与安全**
- 验证码、登录失败锁定与限流、TOTP 双因素（密钥加密存储）、JWT + Redis 令牌、令牌刷新
- 服务端强制口令策略：默认口令首登必须改密、口令过期拦截、改密/重置/停用后会话吊销
- 第三方 OAuth2 登录（微信/GitHub/Gitee）、OAuth 授权码 SSO、API 全局限流
- RBAC：用户、角色、菜单、按钮权限、数据权限（AOP 注解 + SQL 拦截器统一注入）

**企业基础功能**
- 部门、岗位、数据字典、参数配置、通知公告、文件管理、多租户（配额、租户管理员、`tenant_id` 隔离）
- 统一文件服务：本地磁盘 / MinIO 统一 SPI、上传校验、SHA256、病毒扫描、配额、令牌化下载
- 消息中心：系统消息、审批待办、全员广播、已读管理、WebSocket + SSE 实时推送
- 工作流（Warm-Flow）：流程定义、条件/并行节点、审批、驳回/撤回/转办、流程日志
- 定时任务（Quartz JDBC 持久化）、Excel 导入导出、报表与大屏、设备与物模型、表单引擎

**智能互联**
- AI 微服务（FastAPI）：LLM 模型网关、Prompt 管理、RAG 知识库（向量库可选）、文档解析、OCR、文本分类/聚类、PII 出域脱敏
- MCP：平台 SSE 端点对外暴露只读工具，客户端可配置消费外部 MCP Server

**工程与质量**
- 统一错误码、日志脱敏、全表审计字段、字段级审计、逻辑删除 + 乐观锁、Flyway 增量迁移
- 全量中英文国际化、暗黑模式、响应式、PWA；遵循阿里巴巴 Java 开发手册规约（见 `docs/alibaba-compliance.md`）
- CI（GitHub Actions）：多语言构建测试、覆盖率门槛、Playwright E2E、gitleaks/Trivy/CodeQL 安全扫描
- 可观测性：Prometheus 指标、Zipkin 链路追踪、结构化日志、`requestId/traceId`

## 技术栈

| 端 | 技术 |
| --- | --- |
| 管理端 | Vue 3、TypeScript、Vite、Ant Design Vue、Pinia、Vue Router、ECharts、vue-i18n、TanStack Query、Vitest |
| 官网 | Vue 3、TypeScript、Vite |
| Java 后端 | Spring Boot（版本以 `backend/pom.xml` 为准）、Spring Security、MyBatis-Plus、Flyway、Quartz、Redis、JWT、EasyExcel、MinIO、Micrometer |
| AI 服务 | Python 3.11+、FastAPI、Redis、httpx、pytest、Prometheus Client（依赖见 `ai-service/pyproject.toml`） |
| 基础设施 | MySQL 8、Redis 7、Kubernetes、Helm、Argo CD（示例） |

> 版本号类信息一律以各模块清单（`pom.xml` / `package.json` / `pyproject.toml`）为准，本文档不重复罗列以免过期。

## 仓库结构

```text
frontend/    后台管理系统（Vue3 管理端）
website/     对外官网（Vue3）
backend/     Spring Boot 后端与 Flyway 迁移（新增迁移一律 V(n+1)，不改已发布文件）
ai-service/  FastAPI AI 服务
docs/        文档：架构、规约、接口、数据库、部署、运维
k8s/         Kubernetes Helm Chart 与监控清单
gitops/      Argo CD 交付示例
e2e/         Playwright 端到端测试
scripts/     开发/运维脚本与 CRUD 生成器
```

## 快速开始

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

Windows 一键启动：

```powershell
scripts/start-dev.ps1
```

手动启动（四个终端）：

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run      # :8080
cd ai-service && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev                          # :5173
cd website && pnpm install && pnpm dev --port 5174               # 官网
```

访问入口：

- 后台管理系统：http://localhost:5173
- 官网：http://localhost:5174
- 后端接口文档（dev profile）：http://localhost:8080/doc.html

> **默认口令与安全基线**：`admin / admin123` 仅存在于 dev/test 环境用于本地体验。后端默认以 `prod` profile 启动：未注入密钥即启动失败（fail-fast），生产不存在可用的默认凭据；即便数据库被以默认口令初始化，服务端也会强制首次登录改密并拦截未改密账号的业务请求。请勿在生产使用任何演示口令。

MySQL/Redis 就绪后，后端启动时由 Flyway 自动执行迁移目录下全部迁移完成建表与基础数据初始化。

## Kubernetes 与 Helm

生产部署以 Helm Chart（`k8s/helm/admin-scaffold`）为唯一来源（含 HPA、PDB、Ingress、NetworkPolicy）：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace
```

- 密钥一律通过 `--set secret.*` 或外部 Secret（External Secrets）注入，留空时模板 fail-fast。
- 镜像默认不可用公共 `latest` 以外的假设：生产请注入不可变 tag 与 imagePullSecrets。
- 详细部署与前置条件见 `k8s/README.md` 与 `docs/deploy/README.md`。

## 测试与日常运维脚本

```text
scripts/start-dev.ps1 / stop-dev.ps1   启动/停止本地栈
scripts/smoke.ps1                      冒烟
scripts/backup.ps1 / restore.ps1       备份/恢复（MySQL + Redis + MinIO）
scripts/load-test.ps1 / load-test-multi.ps1  压测
scripts/fetch-openapi.ps1              拉取 OpenAPI 契约
scripts/verify-cluster.ps1             K8s 集群前置检查
scripts/crud-gen/                      CRUD 骨架生成器（见其 README）
```

CI 流水线见 `.github/workflows/ci.yml`；GitOps 交付见 `gitops/README.md`。

## 文档地图

| 文档 | 内容 |
| --- | --- |
| `docs/architecture.md` | 系统架构总览 |
| `docs/architecture-conventions.md` | 开发规约（安全/数据权限/文件/迁移等硬性约定） |
| `docs/api/README.md` | API 约定与契约说明（真实契约以运行时 OpenAPI 为准） |
| `docs/database/README.md` | 数据库设计与迁移规范 |
| `docs/deploy/README.md` | 部署指南（本地栈 / K8s / 生产必配项） |
| `docs/runbook.md` | 运维手册（备份恢复、监控告警、升级回滚、密钥轮换） |
| `docs/ai-service.md` | AI 服务使用与扩展 |
| `docs/alibaba-compliance.md` | 阿里巴巴 Java 开发手册规约执行说明 |
| `docs/branding.md` | 品牌化与重命名指引 |
| `k8s/README.md` | Helm 安装与集群前置 |
| `frontend/README.md` | 管理端前端开发指南 |
| `website/README.md` | 官网说明 |
| `gitops/README.md` | GitOps 交付说明 |
| `CONTRIBUTING.md` | 贡献指南（测试门禁、迁移与文档约定） |
| `SECURITY.md` | 安全漏洞披露流程 |

## 贡献与安全

- 参与开发请先阅读 [`CONTRIBUTING.md`](./CONTRIBUTING.md)：包含各模块测试命令、数据库迁移纪律与文档约定。
- 发现安全问题请按 [`SECURITY.md`](./SECURITY.md) 的私有渠道报告，勿直接开公开 issue。
