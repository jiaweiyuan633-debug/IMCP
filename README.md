# Y15 智能管理平台

面向企业生产管理的全栈平台：管理员使用 **后台管理系统**，外部客户与访客访问 **Y15智能管理平台** 官网。仓库包含 Java 业务后端、Python AI 服务、Vue3 管理端与 Vue3 官网，可作为企业生产管理和 Vibe Coding 二次开发的基线。

## 核心能力

- 认证与安全：验证码、登录失败锁定、登录限流、TOTP 2FA（密钥加密存储）、API 全局限流、JWT + Redis Token、Token 刷新、个人资料编辑
- RBAC 权限：用户、角色、菜单、按钮权限、数据权限（AOP 注解 + SQL 拦截器统一注入）
- 企业基础：部门、岗位、数据字典、参数配置、通知公告、文件管理
- 统一文件服务：本地磁盘/MinIO 统一 SPI、上传校验、文件分类、SHA256、ClamAV 病毒扫描、存储配额、签名内容端点、鉴权下载
- 消息中心：系统消息、审批待办、全员广播、未读角标、已读管理、顶部铃铛聚合消息与公告、WebSocket + SSE 实时推送
- 工程能力：Quartz JDBC 持久化定时任务、Excel 导入导出、OpenAPI
- 数据可靠性：全表审计字段、审计日志、敏感字段脱敏、`version` 乐观锁、逻辑删除、Flyway V1-V31
- 工程规范：阿里巴巴 Java 开发手册分批整改、统一错误码、日志脱敏、核心 Service 单测、Manager 分层
- 实时协作：SSE Ticket + WebSocket 双通道、未读角标、AI 任务实时状态、服务器/SQL 告警分级与 Webhook
- 完整工作流：基于 Warm-Flow 1.8.9 的流程定义、条件/并行节点、发布/取消发布、表单数据、待办任务、通过/驳回/撤回/转办、审批日志，存量 `sys_process_def` 启动时自动迁移兼容
- 多租户：租户配额、租户管理员、角色/部门/字典/参数/业务表 `tenant_id` 隔离、数据权限联动
- 可观测性：服务器监控、SQL 监控、操作日志、审计日志、Prometheus、`requestId/traceId`
- 前端体验：全量中英文国际化、暗黑模式、移动端响应式、PWA 离线缓存
- 官网转化：Y15智能管理平台提供产品展示、解决方案、定价与预约演示
- 交付质量：GitHub Actions 覆盖后端/前端/AI/官网构建测试、覆盖率门槛、Playwright E2E、CodeQL，冒烟与压测脚本

## 扩展进度

| 批次 | 模块 | 状态 |
| --- | --- | --- |
| 1 | 统一文件服务 | 已完成 |
| 2 | 消息中心 | 已完成 |
| 3 | 工作流升级 Warm-Flow | 已完成 |
| 4 | 多租户深化 | 待执行 |
| 5 | AI 能力增强（模型网关 / Prompt / RAG + Milvus） | 待执行 |
| 6 | 报表与大屏 | 待执行 |
| 7 | 分布式调度 | 待执行 |
| 8 | 认证扩展 | 待执行 |
| 9 | 字段级审计 | 待执行 |
| 10 | 设备管理 | 待执行 |
| 11 | 消息多渠道 | 待执行 |
| 12 | MCP | 待执行 |
| 13 | 可观测性增强 | 待执行 |
| 14 | 前端组件沉淀 | 待执行 |

## 前后端对接优化进度

| 批次 | 范围 | 状态 |
| --- | --- | --- |
| P0 | 消息中心待办接入 Warm-Flow、铃铛聚合消息与公告、公告/消息详情直达 | 已完成 |
| P1 | 工作流双轨收敛、组合查询、文件访问 token、通知统一聚合 | 待执行 |
| P2 | 类型契约、租户管理员候选、工作流详情、前端统一状态与国际化 | 待执行 |

## 技术栈

| 端 | 技术 |
| --- | --- |
| 管理端 | Vue 3、TypeScript、Vite 7、Ant Design Vue 4、Pinia、Vue Router、ECharts、vue-i18n、TanStack Query、Vitest |
| 官网 | Vue 3、Vite 7、lucide-vue-next |
| Java 后端 | Spring Boot 3.3、Spring Security 6、MyBatis-Plus、Flyway、Quartz、Redis、JWT、EasyExcel、MinIO、Micrometer、Knife4j |
| AI 服务 | FastAPI、Redis、httpx、pytest、Prometheus Client |
| 基础设施 | MySQL 8、Redis 7、Docker Compose、Kubernetes、Helm |

## 仓库结构

```text
frontend/    后台管理系统（Vue3 管理端）
website/     Y15智能管理平台官网
backend/     Spring Boot 后端与 Flyway 脚本（当前 V1-V32）
ai-service/  FastAPI AI 服务
docs/        接口、数据库、部署、演示材料
docker/      Docker Compose、Nginx 与各端 Dockerfile
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
cd backend && mvn spring-boot:run
cd ai-service && uv sync && uv run uvicorn app.main:app --port 8000
cd frontend && pnpm install && pnpm dev
cd website && pnpm install && pnpm dev --port 5174
```

访问入口：

- 后台管理系统：http://localhost:5173 ，默认管理员：`admin / admin123`
- Y15智能管理平台官网：http://localhost:5174
- 后端接口文档：http://localhost:8080/doc.html

MySQL/Redis 启动后，后端通过 Flyway 自动完成建表与基础数据初始化。

## Docker Compose

```bash
cd docker
docker compose up -d --build
```

Compose 编排 MySQL、Redis、Java 后端、AI 服务、管理端、官网六个服务。可通过 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT`、`WEBSITE_PORT` 覆盖主机端口，配置示例见 [docker/.env.example](docker/.env.example)。

## Kubernetes 与 Helm

使用原生清单：

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

使用 Helm Chart（推荐，包含 HPA 与 Ingress）：

```bash
helm upgrade --install admin-scaffold ./k8s/helm/admin-scaffold \
  --namespace admin-scaffold --create-namespace
```

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
- 演示与答辩材料：`docs/demo-outline.md`
- 架构说明：`docs/architecture.md`
- 架构设计规约：`docs/architecture-conventions.md`
- 运维手册：`docs/runbook.md`
- 阿里规约合规说明：`docs/alibaba-compliance.md`

