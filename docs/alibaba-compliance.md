# 《阿里巴巴 Java 开发手册》规约执行说明

本文说明仓库（Java 后端）如何落实《阿里巴巴 Java 开发手册》的规约：按**命名与领域
模型、并发、异常与错误码、日志、安全、MySQL、工程结构、测试与 CI** 几个维度给出
当前代码中的落地方式、可核对的证据位置，以及自查命令。面向新加入的
backend 开发者与代码评审人，作为“写代码前先读、提交前自查”的对照清单。

> 这是**持续执行的规约基线**，不是一次性清单：新代码在评审中按本文拦截违规，
> 存量代码在改动所属模块时同步收敛。规约细节（分层、命名、错误码、数据访问、测试）
> 见 `docs/architecture-conventions.md`，数据库迁移规范见 `docs/database/README.md`。

## 命名与领域模型

- 包基座 `cn.admin.scaffold`，业务代码按 `module.<域>` 分包
  （`controller / service / manager / mapper / entity / dto / vo`），避免跨模块
  互相 new。
- 数据库实体统一以 `DO` 结尾（如 `SysUserDO`、`AiTaskDO`、`AiServiceConfigDO`），
  对外传输对象按职责命名（`dto` 入参、`vo` 出参）；状态字段收敛为枚举而非散落
  魔法值（如 `WorkflowStatus`、`AiTaskStatus`）。
- 常量收敛：业务默认值、状态、错误语义使用枚举/常量类集中管理，不写裸字面量。
- 自查：`git grep -n 'com\.example' backend/src`（应无生产代码残留）；
  评审时检查“新业务状态是否新增了枚举/常量”。

## 日期时间与并发

- 业务代码使用 `java.time`；`Date` 仅在 JWT、外部 SDK 等边界做转换；
  不使用 `SimpleDateFormat`（线程不安全）、`java.sql.Date`。
- 线程池不直接使用 `Executors` 默认工厂：显式指定线程名、队列与拒绝策略
  （AI 实时推送等场景使用 Spring 的 `ThreadPoolTaskScheduler` / 受管执行器），
  由 Spring 容器统一生命周期管理。
- 多实例任务使用分布式锁（Redis/Redisson）或调度表互斥，避免重复执行。
- 自查：`git grep -n 'Executors\.new' backend/src/main`、
  `git grep -n 'SimpleDateFormat\|new java\.sql\.Date\|new Date()' backend/src/main`。

## 异常与错误码

- 业务异常统一抛 `BusinessException`，错误码集中定义在 `ResultCode` 枚举
  （业务码 `1001` 起；HTTP 语义错误 `401/403/404/405/413/415/429/500` 使用
  对应枚举）；`GlobalExceptionHandler`（`common/GlobalExceptionHandler.java`）
  负责参数校验、约束、类型不匹配、数据完整性、认证异常等兜底映射，Handler 之外
  不得自行拼装错误响应。
- 约定：业务校验错误返回 **HTTP 200 + 业务码**，HTTP 层语义错误返回对应状态码，
  响应体结构一致（详见 `docs/api/README.md` 错误码一节）。
- 捕获边界：只捕获可预期的异常类型（业务、JSON、安全、Quartz、SpEL、SDK 已知
  异常等），不写无差别的 `catch (Exception)` 吞异常；外部 SDK（如 MinIO、文件
  清理）按 SDK 抛出的已知异常精确捕获。
- 新增错误码必须同步前端语言包 `frontend/src/locales/zh-CN.ts` 与
  `en-US.ts`；错误码一经发布不删除、不改变语义。
- 自查：`git grep -n 'catch (Exception' backend/src/main`（应只在确有必要的边界，
  且需在评审说明理由）。

## 日志

- 统一 SLF4J + Logback（`backend/src/main/resources/logback-spring.xml`），
  业务日志经 `log.error/info/...` 输出；代码不使用 `printStackTrace` 与
  `System.out` 拼日志。
- `requestId` 贯穿：`RequestIdFilter` 生成/透传，日志 pattern 带 `[%X{requestId}]`，
  与 HTTP 响应头/响应体一致，便于按请求串日志。
- 敏感字段脱敏：`LogMaskUtils`（`common/LogMaskUtils.java`）对操作日志做递归脱敏
  （密码、Token、API Key、手机号、邮箱、密钥类字段整值打码）；对象属性级脱敏按
  注解/字段名单维护。
- 审计：操作日志（`@OperLog`）、字段级审计与登录日志由 AOP/Manager 统一落库，
  不散落在业务代码里。
- 自查：`git grep -n 'printStackTrace\|System\.out' backend/src/main`。

## 安全规约

- **密钥管理**：`JWT_SECRET`、`TOTP_ENCRYPTION_KEY`、DB 口令、AI/MCP 令牌一律由
  环境/Secret 注入，代码与配置不留默认值（prod profile 缺失即启动失败）；
  TOTP 密钥与 AI 服务 `apiKey` 密文落库（`SecretCipher`，`enc:` 前缀幂等加密），
  日志与回显只透出“是否已配置”等非敏感信息。
- **文件安全**：文件上传校验类型/大小/SHA256，可选病毒扫描（ClamAV，支持
  fail-open 配置）；文件内容经一次性签名 Token 访问，不暴露裸路径。
- **注入与越权**：SQL 走 MyBatis-Plus 参数化；报表等自定义 SQL 由
  `ReportSqlGuard`（`module/report/ReportSqlGuard.java`）守卫只读能力与危险语句；
  访问控制走 RBAC 权限注解 + 数据权限拦截（AOP 注解 + SQL 拦截器注入 `tenant_id`
  与行级条件）。
- **出站 SSRF**：回调/外部 URL 出站前经校验（后端 AI 配置 baseUrl 校验、ai-service
  回调白名单），拒绝云元数据/链路本地等危险地址段。
- 自查：代码评审核对“新接口是否加权限注解与数据权限、新配置是否有默认密钥”。

## MySQL 与迁移

- **所有 schema/数据变更走 Flyway**：在 `backend/src/main/resources/db/migration`
  新增 `V{n}__*.sql`（n 为下一个序号），**禁止修改已执行过的脚本**；规则与示例见
  `docs/database/README.md`。
- 自研 SQL 显式列出字段（自定义 SQL 不使用 `SELECT *`）；表补审计字段
  （`created_at/updated_at`）与高频查询组合索引属于建表评审项，随迁移补齐。
- 逻辑删除统一走 MyBatis-Plus 全局配置（`application.yml` 中
  `logic-delete-field: deleted`），实体与迁移按该约定补字段，避免散落手写
  `delete` 语句绕过。
- 自查：评审时核对“是否有绕过 Flyway 的手工 DDL、自定义 SQL 是否显式列名”。

## 工程结构

- 分层依赖单向：`controller → service → manager → mapper/外部客户端`；外部 AI、
  Webhook、存储客户端由 `manager` 层封装（`module/ai/manager/AiTaskManager`、
  `module/monitor/manager/AlertWebhookManager`），Service 只做业务编排，不在
  Service 里直接持有 HTTP/Redis 客户端。
- 实体/枚举/常量按域就近放置，禁止跨模块反向依赖。

## 测试与 CI 手段

- 后端：单元测试（surefire）+ Testcontainers 集成测试（`*IT`，MySQL/Redis 容器 +
  Flyway 迁移）在 `mvn verify` 一并执行；质量门禁：JaCoCo 覆盖率（LINE/BRANCH/
  METHOD 阈值以 `backend/pom.xml` jacoco `check` 配置为准）与 SpotBugs
  （`failOnError=true`，豁免白名单 `backend/spotbugs-exclude.xml`）。
- AI 服务：pytest（`fakeredis` + `mock` LLM，覆盖率门禁见 `pyproject.toml`
  `addopts`）+ ruff。
- 前端：lint + Vitest 覆盖率（`frontend/vitest.config.ts` thresholds）。
- CI（`.github/workflows/ci.yml`）每次提交执行上述全部检查，另含 Gitleaks/Trivy/
  CodeQL 安全扫描——新提交的规约问题会在门禁上暴露。
- IDE 静态检查插件（如 Alibaba Java Coding Guidelines）可作为本地辅助，但仓库以
  上述 CI 门禁与评审为准，不依赖个人环境。

## 提交前自查清单

1. 新错误码已进 `ResultCode` 并同步 `zh-CN.ts` / `en-US.ts`；
2. 无 `printStackTrace` / `System.out` / 裸 `Executors` / 新 `SimpleDateFormat`；
3. 状态与魔法值已收敛为枚举/常量；
4. 数据库变更走新 Flyway 迁移，未改历史脚本；
5. 新接口有权限注解与数据权限；新外部出站 URL 有 SSRF 校验；
6. `cd backend && mvn verify` 与本地 `git grep` 自查通过。
