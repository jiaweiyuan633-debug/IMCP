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
| 安全规约 | 敏感信息 | TOTP 密钥加密、文件签名访问、日志不输出敏感字段 |
| MySQL | SQL | 自定义 SQL 已移除 `SELECT *`，改为显式字段 |
| MySQL | 建表 | V26 为关联表补齐 `created_at/updated_at` |
| 工程结构 | 分层 | 新增 `ai/manager/AiTaskManager`，Service 不再直接依赖外部客户端 |

## 残余整改项

| 模块 | 条款 | 说明 |
| --- | --- | --- |
| 命名规约 | 实体类 DO 后缀 | 现有 `Sys*` 实体未统一改为 `Sys*DO`，改动面大，建议独立批次 |
| 编程规约 | 魔法值清理 | 部分业务数字仍硬编码，逐步收敛常量 |
| MySQL | 主键 UNSIGNED | 现有 `id BIGINT` 未改为 `BIGINT UNSIGNED`，需要评估迁移影响 |
| 工程结构 | Manager 覆盖度 | 目前 AI 已拆 Manager，文件、告警等外部能力仍为 Service 直连 |
| 异常日志 | 外部 SDK 边界 | `SysFileService` 文件对象删除仍保留单点 `catch (Exception)`，避免 SDK 多异常类耦合 |

## 后续建议

- 新增实体统一使用 `DO` 后缀，DTO/VO 保持现有命名
- 定期用 Ali 规约扫描插件做静态检查
- 每次数据库变更都通过 Flyway，并同步更新本文档
- 新错误码需同步维护前端 `zh-CN.ts` / `en-US.ts` 语言包
