# 双端管理脚手架

一套可运行的“Java 业务后端 + Python AI 服务 + Vue3 管理端”双端管理脚手架，支持本地开发和 Docker Compose 部署。

## 技术栈

| 端 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite 7、Ant Design Vue 4、Pinia、Vue Router、ECharts |
| Java 后端 | Spring Boot 3.3、Spring Security 6、MyBatis-Plus、Flyway、Redis、JWT |
| AI 服务 | FastAPI、Pydantic、Redis、httpx、pytest |
| 基础设施 | MySQL 8、Redis 7（兼容 Memurai）、Docker Compose |

## 仓库结构

```text
frontend/    Vue3 管理端
backend/     Spring Boot 后端与 Flyway 脚本
ai-service/  FastAPI AI 服务
docs/        接口、数据库、部署、演示材料
docker/      Docker Compose 与 Nginx 配置
scripts/     开发启动、停止与冒烟脚本
```

## 本地启动

环境要求：Java 21、Maven 3.9+、Node.js 20+、pnpm、Python 3.11+（可用 uv 托管）、MySQL 8、Redis 7。

1. 初始化数据库：

```bash
mysql -uroot -p -e "CREATE DATABASE admin_scaffold DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

2. 启动后端：

```bash
cd backend
mvn spring-boot:run
```

3. 启动 AI 服务：

```bash
cd ai-service
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000
```

4. 启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

访问 http://localhost:5173 ，默认管理员：`admin / admin123`。

## Docker Compose 启动

```bash
cd docker
docker compose up -d --build
```

服务地址：

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost |
| Java 后端 | http://localhost:8080 |
| AI 服务 | http://localhost:8000 |
| 接口文档 | http://localhost:8080/doc.html |

## 冒烟测试

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1
```

## 文档入口

- 接口文档：`docs/api/`
- 数据库设计：`docs/database/`
- 部署教程：`docs/deploy/`
- 演示与答辩材料：`docs/demo-outline.md`

