# 数据库设计

Flyway 脚本是唯一事实来源，位于 `backend/src/main/resources/db/migration/`，当前版本 V1-V7。

## 表清单

### 系统与权限

| 表 | 说明 |
| --- | --- |
| `sys_user` | 用户，含部门、审计字段、乐观锁 |
| `sys_role` | 角色，含数据权限范围 |
| `sys_menu` | 菜单与按钮权限 |
| `sys_user_role` | 用户角色 |
| `sys_role_menu` | 角色菜单 |
| `sys_role_dept` | 角色自定义数据权限部门 |
| `sys_dept` | 部门 |
| `sys_post` | 岗位 |
| `sys_user_post` | 用户岗位 |

### 基础数据

| 表 | 说明 |
| --- | --- |
| `sys_dict_type` | 字典类型 |
| `sys_dict_data` | 字典数据 |
| `sys_config` | 参数配置 |
| `sys_tenant` | 租户 |

### 日志与监控

| 表 | 说明 |
| --- | --- |
| `sys_login_log` | 登录日志 |
| `sys_oper_log` | 操作日志 |
| `sys_sql_log` | SQL 监控日志 |
| `sys_job` | 定时任务 |
| `sys_job_log` | 任务日志 |

### 业务与消息

| 表 | 说明 |
| --- | --- |
| `sys_notice` | 通知公告 |
| `sys_notice_read` | 通知已读 |
| `sys_file` | 文件元数据 |
| `sys_workflow` | 简化工作流 |

### AI

| 表 | 说明 |
| --- | --- |
| `ai_service_config` | AI 服务配置 |
| `ai_task` | AI 任务 |
| `ai_task_result` | AI 任务结果 |

## 关键设计

- 用户、角色、部门、岗位使用逻辑删除。
- `sys_user` 带 `version` 乐观锁和 `created_by/updated_by` 审计字段。
- 权限与字典/参数支持 Redis 缓存，权限变更自动失效缓存。
- SQL 日志阈值由 `SQL_LOG_THRESHOLD_MS` 控制，默认 50ms。
- 数据库变更必须新增 Flyway 脚本。

