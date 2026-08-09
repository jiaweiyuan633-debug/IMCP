# 双端管理脚手架

面向企业生产管理的“Java 业务后端 + Python AI 服务 + Vue3 管理端”全栈脚手架，已从演示脚手架升级为企业级基线。

## 核心能力

- RBAC 权限：用户、角色、菜单、按钮权限、数据权限
- 企业基础：部门、岗位、数据字典、参数配置
- 工程能力：定时任务、文件上传、Excel 导入导出、通知公告
- 可观测性：服务器监控、SQL 监控、Prometheus 指标、结构化日志、traceId
- 安全基线：验证码、登录失败锁定、登录限流、上传内容校验
- 演进预留：租户管理、简化工作流、K8s 清单、压测脚本
- CI/CD：GitHub Actions 自动执行后端/前端/AI 测试与构建

## 技术栈

| 端 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite 7、Ant Design Vue 4、Pinia、Vue Router、ECharts、vue-i18n、TanStack Query、Vitest |
| Java 后端 | Spring Boot 3.3、Spring Security 6、MyBatis-Plus、Flyway、Quartz、Redis、JWT、EasyExcel、Micrometer |
| AI 服务 | FastAPI、Redis、httpx、pytest、Prometheus Client |
| 基础设施 | MySQL 8、Redis 7、Docker Compose、Kubernetes |

## 仓库结构

```text
frontend/    Vue3 管理端
backend/     Spring Boot 后端与 Flyway 脚本
ai-service/  FastAPI AI 服务
docs/        接口、数据库、部署、演示材料
docker/      Docker Compose 与 Nginx 配置
k8s/         Kubernetes 清单
scripts/     启动、停止、冒烟、备份、压测、OpenAPI 脚本
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

访问 http://localhost:5173 ，默认管理员：`admin / admin123`。

## Docker Compose

```bash
cd docker
docker compose up -d --build
```

可通过 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT`、`AI_PORT`、`FRONTEND_PORT` 覆盖主机端口。

## Kubernetes

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/manifests.yaml
```

详见 [k8s/README.md](k8s/README.md)。

## 测试与运维

```powershell
scripts/smoke.ps1
scripts/backup.ps1
scripts/load-test.ps1
scripts/fetch-openapi.ps1
```

CI 流水线见 `.github/workflows/ci.yml`。

## 文档入口

- 接口文档：`docs/api/`
- 数据库设计：`docs/database/`
- 部署教程：`docs/deploy/`
- 演示与答辩材料：`docs/demo-outline.md`

