# 数据库设计

Flyway 脚本是唯一事实来源，位于 `backend/src/main/resources/db/migration/`，当前版本 V1-V22。

## 版本记录

| 版本 | 内容 |
| --- | --- |
| V1 | 初始系统表：用户、角色、菜单、部门、岗位、字典、参数、日志、AI 表 |
| V2 | 按钮权限与菜单种子数据 |
| V3 | 企业基础模块：部门、岗位、字典、参数配置 |
| V4 | 数据权限、定时任务表与基础任务 |
| V5 | 监控、通知公告、看板统计表 |
| V6 | 审计字段、通知已读、文件元数据 |
| V7 | 租户、简化工作流与菜单权限 |
| V8 | 核心业务表审计字段与乐观锁 |
| V9 | 租户隔离字段与工作流审批日志 |
| V10 | 告警规则表与菜单权限 |
| V11 | 文件管理菜单权限 |
| V12 | 业务表租户隔离字段 |
| V13 | 流程定义、流程节点与工作流引擎字段 |
| V14 | 后台管理系统通知文案更新 |
| V15 | 告警规则分级、静默期与 Webhook |
| V16 | 工作流转办与撤回字段 |
| V17 | 角色、部门、岗位、字典、参数租户隔离 |
| V18 | 审计日志表 |
| V19 | 审计日志菜单权限 |
| V20 | TOTP 两步验证字段 |
| V21 | 租户用户/存储配额与管理员 |
| V22 | AI 服务每日任务限额 |

## 表清单

### 系统与权限

| 表 | 说明 |
| --- | --- |
| `sys_user` | 用户，含部门、租户、审计字段、乐观锁 |
| `sys_role` | 角色，含数据权限范围、审计字段、乐观锁 |
| `sys_menu` | 菜单与按钮权限 |
| `sys_user_role` | 用户角色 |
| `sys_role_menu` | 角色菜单 |
| `sys_role_dept` | 角色自定义数据权限部门 |
| `sys_dept` | 部门，含审计字段、乐观锁 |
| `sys_post` | 岗位，含审计字段、乐观锁 |
| `sys_user_post` | 用户岗位 |

### 基础数据

| 表 | 说明 |
| --- | --- |
| `sys_dict_type` | 字典类型，含审计字段、乐观锁 |
| `sys_dict_data` | 字典数据，含审计字段、乐观锁 |
| `sys_config` | 参数配置，含审计字段、乐观锁 |
| `sys_tenant` | 租户，含审计字段、乐观锁 |

### 日志与监控

| 表 | 说明 |
| --- | --- |
| `sys_login_log` | 登录日志 |
| `sys_oper_log` | 操作日志 |
| `sys_sql_log` | SQL 监控日志 |
| `sys_job` | 定时任务，含审计字段、乐观锁 |
| `sys_job_log` | 任务日志 |
| `sys_alert_rule` | 服务器告警规则，含租户 |
| `sys_audit_log` | 审计日志，含租户、操作参数与结果 |

### 业务与消息

| 表 | 说明 |
| --- | --- |
| `sys_notice` | 通知公告，含审计字段、乐观锁 |
| `sys_notice_read` | 通知已读 |
| `sys_file` | 文件元数据，含租户、存储类型 |
| `sys_workflow` | 工作流实例，含流程定义、当前节点、审计字段、乐观锁 |
| `sys_workflow_log` | 工作流审批日志 |
| `sys_process_def` | 流程定义，含租户、唯一流程标识 |
| `sys_process_node` | 流程节点，含审批角色 |

### AI

| 表 | 说明 |
| --- | --- |
| `ai_service_config` | AI 服务配置 |
| `ai_task` | AI 任务 |
| `ai_task_result` | AI 任务结果 |

## 关键设计

- 用户、角色、部门、岗位使用逻辑删除。
- `created_by/updated_by` 由 `MyMetaObjectHandler` 自动填充，核心业务表带 `version` 乐观锁并由 `OptimisticLockerInnerInterceptor` 校验。
- 租户隔离：`sys_user`、`sys_file`、`sys_notice`、`sys_job`、`sys_workflow`、日志与 AI 业务表均带 `tenant_id`，MyBatis-Plus 租户拦截器按当前 `TenantContext` 自动追加条件；自定义 `<script>` Mapper 方法通过 `@InterceptorIgnore(tenantLine = "true")` 处理。
- 数据权限：角色支持全部数据、本部门、本部门及以下、自定义部门四种范围，查询时由 `DataScopeHelper` 统一注入。
- 权限、字典、参数支持 Redis 缓存，权限变更自动失效缓存。
- 文件元数据统一写入 `sys_file`，存储后端支持本地目录与 MinIO。
- SQL 日志阈值由 `SQL_LOG_THRESHOLD_MS` 控制，默认 50ms。
- 定时任务使用 Quartz JDBC 存储，任务定义与执行日志持久化。
- 工作流引擎包含流程定义、审批节点、待办任务与审批日志，支持按角色流转。
- 通知实时推送使用 SSE，告警规则由定时任务检查并写入通知公告。
- SSE 使用一次性 Ticket 建立连接，避免 Token 出现在 URL。
- 告警支持 `INFO/WARNING/CRITICAL` 分级、静默期与 Webhook 推送。
- 工作流支持发起人撤回和审批转办。
- 租户支持用户上限、存储配额与管理员绑定。
- AI 服务支持每日任务限额，非管理员查看用户敏感字段自动脱敏。
- 所有数据库变更必须新增 Flyway 脚本，禁止手工改生产库。

