# Y15 双端管理脚手架

面向企业生产管理的“Java 业务后端 + Python AI 服务 + Vue3 管理端”全栈脚手架。已覆盖企业基础、工程能力、安全基线、可观测性、租户与工作流等生产级能力，同时保持精简结构，适合作为 Vibe Coding 快速二次开发的基线（按需移除代码生成器）。

## 核心能力

- 认证与安全：验证码、登录失败锁定、登录限流、JWT + Redis Token 管理、Token 刷新、个人资料编辑
- RBAC 权限：用户、角色、菜单、按钮权限、数据权限（部门级与自定义范围）
- 企业基础：部门、岗位、数据字典、参数配置、通知公告
- 工程能力：Quartz JDBC 持久化定时任务、文件上传（本地/MinIO）、用户 Excel 导入导出、文件元数据
- 数据可靠性：全表审计字段、`version` 乐观锁、逻辑删除、Flyway 数据库版本管理
- 可观测性：服务器监控、SQL 监控、登录/操作日志、Prometheus 指标、结构化日志与 `requestId/traceId`
- 演进能力：租户隔离、简化工作流与审批日志、K8s 清单与 Helm Chart、全链路压测脚本
- 交付质量：GitHub Actions 自动执行后端/前端/AI 测试与构建，冒烟脚本覆盖主流程

## 技术栈

| 端 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite 7、Ant Design Vue 4、Pinia、Vue Router、ECharts、vue-i18n、TanStack Query、Vitest |
| Java 后端 | Spring Boot 3.3、Spring Security 6、MyBatis-Plus、Flyway、Quartz、Redis、JWT、EasyExcel、MinIO、Micrometer、Knife4j |
| AI 服务 | FastAPI、Redis、httpx、pytest、Prometheus Client |
| 基础设施 | MySQL 8、Redis 7、Docker Compose、Kubernetes、Helm |

## 仓库结构

```text
frontend/    Vue3 管理端
backend/     Spring Boot 后端与 Flyway 脚本（当前 V1-V9）
ai-service/  FastAPI AI 服务
docs/        接口、数据库、部署、演示材料
docker/      Docker Compose、Nginx 与各端 Dockerfile
k8s/         Kubernetes 清单与 Helm Chart
scripts/     启动、停止、冒烟、备份、恢复、压测、OpenAPI 脚本
```

## 本地启动

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+、MySQL 8、Redis 7。

```bash
cd backend
mvn spring-boot:run

cd ai-service
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000

cd frontend
pnpm install
pnpm dev
```

访问 http://localhost:5173 ，默认管理员：`admin / admin123`。MySQL/Redis 启动后，后端通过 Flyway 自动完成建表与基础数据初始化。

## Docker Compose

```bash
cd docker
docker compose up -d --build
```

Compose 编排 MySQL、Redis、Java 后端、AI 服务、Nginx 前端五个服务。可通过 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT` 覆盖主机端口，配置示例见 [docker/.env.example](docker/.env.example)。

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
scripts/smoke.ps1
scripts/backup.ps1
scripts/restore.ps1
scripts/load-test.ps1
scripts/load-test-multi.ps1
scripts/fetch-openapi.ps1
```

CI 流水线见 `.github/workflows/ci.yml`。

## 文档入口

- 接口文档：`docs/api/`
- 数据库设计：`docs/database/`
- 部署教程：`docs/deploy/`
- 演示与答辩材料：`docs/demo-outline.md`

