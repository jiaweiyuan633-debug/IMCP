# 数据库设计

数据库脚本以 Flyway 为唯一事实来源，位于：

```text
backend/src/main/resources/db/migration/
```

## 表清单

| 表名 | 说明 |
| --- | --- |
| `sys_user` | 用户 |
| `sys_role` | 角色 |
| `sys_menu` | 菜单与按钮权限 |
| `sys_user_role` | 用户角色关联 |
| `sys_role_menu` | 角色菜单关联 |
| `sys_login_log` | 登录日志 |
| `sys_oper_log` | 操作日志 |
| `sys_dept` | 部门 |
| `sys_post` | 岗位 |
| `sys_user_post` | 用户岗位关联 |
| `sys_dict_type` | 字典类型 |
| `sys_dict_data` | 字典数据 |
| `sys_config` | 参数配置 |
| `sys_job` | 定时任务 |
| `sys_job_log` | 定时任务日志 |
| `sys_role_dept` | 角色数据权限部门关联 |
| `sys_sql_log` | SQL 监控日志 |
| `sys_notice` | 通知公告 |
| `ai_service_config` | AI 服务配置 |
| `ai_task` | AI 任务 |
| `ai_task_result` | AI 任务结果 |

## 关键设计

- 用户、角色、菜单采用 RBAC 模型。
- 用户、角色、日志统一使用逻辑删除字段 `deleted`。
- `ai_task.task_no` 全局唯一，回调按任务号幂等。
- `ai_task.status` 支持 `PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`。
- 数据库结构变更必须新增 Flyway 脚本，不允许手工改表。

## 初始化

```sql
CREATE DATABASE admin_scaffold
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

后端启动时 Flyway 自动执行迁移，默认管理员 `admin / admin123`。

