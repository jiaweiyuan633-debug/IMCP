# 贡献指南

感谢你参与智能管理平台（admin-scaffold）的开发。本仓库定位为**企业生产脚手架**：任何改动都应当可被下游业务项目安全继承。请遵循以下约定。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 业务后端（含 Flyway 迁移） |
| `ai-service/` | FastAPI AI 微服务 |
| `frontend/` | 管理端 SPA（Vue 3 + TS） |
| `website/` | 对外官网（Vue 3 + Vite） |
| `e2e/` | Playwright 端到端测试 |
| `k8s/` | Helm Chart 与监控清单 |
| `gitops/` | ArgoCD 交付示例 |
| `scripts/` | 开发/运维脚本与 CRUD 生成器 |
| `docs/` | 面向开发者的文档（接口、数据库、部署、架构） |

## 环境要求

- Java 21、Maven 3.9+、Node.js 20+（推荐 22）、pnpm 11+、Python 3.11+、MySQL 8、Redis 7
- 本地开发：`scripts/start-dev.ps1`（Windows）或按 `README.md` 手动启动

## 开发流程

1. 从 `dev` 分支新建特性分支：`git checkout -b feat/your-change`
2. 编写代码并**保持改动内聚**：一个提交只做一件事
3. 代码风格：
   - Java：遵循阿里巴巴 Java 开发手册，统一错误码，Service/Manager 分层
   - 前端：TypeScript `strict`，组件与状态遵循 `docs/architecture-conventions.md`
   - Python：使用 ruff（格式化 + lint）
4. 提交信息建议使用规范前缀（`feat:` / `fix:` / `docs:` / `refactor:` / `chore:`）

## 测试要求

任何改动必须通过对应模块的既有门禁，新增功能尽量补测试：

- 后端：`cd backend && mvn verify`（含 JaCoCo 覆盖率门槛）
- 前端：`cd frontend && pnpm lint && pnpm exec vitest run --coverage && pnpm build`
- AI 服务：`cd ai-service && uv run pytest --locked && uv run ruff check .`
- 官网：`cd website && pnpm build`
- 端到端：`cd e2e && pnpm run typecheck && pnpm exec playwright test`

## 数据库变更约定

- **禁止修改已发布的迁移文件**（`V1`~`V63`），变更一律新增 `V(n+1)__*.sql`
- 新表主键统一 `BIGINT`，含 `tenant_id / created_by / created_at / updated_by / updated_at / deleted / version` 审计列
- 新表如使用业务编码唯一键，须考虑逻辑删除后同名重建场景（唯一键与 `deleted` 的取舍见 `docs/database/README.md`）
- 每张表必须有可支撑"租户条件 + 主要查询条件"的索引

## 文档约定

- 仓库文档面向**开发者阅读**，采用稳定表述：不保留按编号逐条罗列的改动记录、进度状态表或临时交付措辞，机械事实以代码为单一事实源。
- 接口、表清单、版本号等机械事实一律以**生成物/代码为单一事实源**；手写文档只做导读
- 涉及默认口令、密钥、端口等事实修改时，同步更新相关文档

## 分支与发布

- `main` 为可发布分支，`dev` 为集成分支
- 合并 PR 前必须通过 CI 全部检查
- 发布时打 `v*.*.*` tag 并附 GitHub Release 说明

## 行为准则

保持开放、尊重、专业的协作氛围。评审意见针对代码而非个人。
