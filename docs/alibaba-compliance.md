# 阿里巴巴 Java 开发手册合规说明

## 已完成整改

| 模块 | 条款 | 当前处理 |
| --- | --- | --- |
| 编程规约 | 日期时间 | 业务代码改用 `java.time`，`Date` 仅在 JWT 边界转换 |
| 编程规约 | 常量与魔法值 | 工作流状态收敛为 `WorkflowStatus`，AI 状态使用 `AiTaskStatus` |
| 编程规约 | 集合初始化 | 主要外部调用 Map/List 按预估容量初始化 |
| 并发编程 | 线程池 | AI 实时推送使用 `ThreadPoolTaskScheduler`，不再使用 `Executors` |
| 异常日志 | 日志框架 | 无 `printStackTrace`，统一使用 `log.error` |
| 异常日志 | 异常捕获 | 业务层按 `BusinessException`、JSON、Security、Quartz、SpEL 等已知异常精确捕获，减少 `catch (Exception)` |
| 异常日志 | 统一错误码 | `ResultCode` 新增 1019-1025，验证码、TOTP、AI 回调、工作流状态统一错误码 |
| 异常日志 | 统一异常处理 | `GlobalExceptionHandler` 补充参数解析、约束校验、数据完整性、认证异常映射 |
| 安全规约 | 日志脱敏 | 新增 `LogMaskUtils`，操作日志递归脱敏密码、Token、API Key、手机号、邮箱等字段 |
| 单元测试 | 覆盖率 | 后端 JaCoCo、前端 Vitest Coverage 阈值已接入 CI |
| 单元测试 | 核心 Service | 新增租户、用户、AI 任务、工作流、告警 Manager 单测，当前后端 17 个用例全通过 |
| 安全规约 | 敏感信息 | TOTP 密钥加密、文件签名访问、日志不输出敏感字段 |
| MySQL | SQL | 自定义 SQL 已移除 `SELECT *`，改为显式字段 |
| MySQL | 建表 | V26 为关联表补齐 `created_at/updated_at` |
| MySQL | 索引 | V27 为登录日志、操作日志、工作流、AI 任务、公告、定时任务补齐高频查询组合索引 |
| 工程结构 | 分层 | 新增 `ai/manager/AiTaskManager`，Service 不再直接依赖外部客户端 |
| 工程结构 | Manager | 告警 Webhook 拆分 `AlertWebhookManager`，Service 只保留业务编排 |
| 设计规约 | 文档 | 新增 `docs/architecture-conventions.md`，固化分层、命名、错误码、数据访问与测试规约 |
| 编程规约 | 魔法值 | AI、租户、用户、登录限流、定时任务、告警、流程、菜单等业务默认值收敛为常量 |
| 编程规约 | BigDecimal/日期/并发 | 复查无 `BigDecimal.equals` 等值比较、无 `SimpleDateFormat`/`Executors`/`java.sql.Date` |

## 残余整改项

| 模块 | 条款 | 说明 |
| --- | --- | --- |
| 命名规约 | 实体类 DO 后缀 | 现有 `Sys*` 实体未统一改为 `Sys*DO`，改动面大，建议独立批次 |
| 编程规约 | 魔法值清理 | 部分业务数字仍硬编码，逐步收敛常量 |
| MySQL | 主键 UNSIGNED | 现有 `id BIGINT` 未改为 `BIGINT UNSIGNED`，需要评估迁移影响 |
| 异常日志 | 外部 SDK 边界 | `SysFileService` 文件对象删除仍保留单点 `catch (Exception)`，避免 SDK 多异常类耦合 |
| 编程规约 | 魔法值清理 | 少量低频服务仍有业务默认值硬编码，后续随模块重构继续收敛 |

## 后续建议

- 新增实体统一使用 `DO` 后缀，DTO/VO 保持现有命名
- 定期用 Ali 规约扫描插件做静态检查
- 每次数据库变更都通过 Flyway，并同步更新本文档
- 新错误码需同步维护前端 `zh-CN.ts` / `en-US.ts` 语言包
