# 接口文档

统一响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "uuid"
}
```

常见错误码：`401` 未登录、`403` 无权限、`1001` 参数错误、`1002` 数据不存在、`1010` AI 服务不可用、`1014` 登录失败次数过多。

## 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/auth/login-config` | 登录配置（验证码开关） |
| GET | `/api/auth/captcha` | 图形验证码 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/auth/me` | 当前用户、权限、菜单树 |
| PUT | `/api/auth/password` | 修改密码 |

## 系统管理

| 模块 | 路径 |
| --- | --- |
| 用户 | `/api/system/user`，含导入导出 |
| 角色 | `/api/system/role`，含菜单/部门授权 |
| 菜单 | `/api/system/menu` |
| 部门 | `/api/system/dept` |
| 岗位 | `/api/system/post` |
| 字典 | `/api/system/dict/type`、`/api/system/dict/data` |
| 参数 | `/api/system/config` |
| 通知公告 | `/api/system/notice`，含未读/已读 |
| 租户 | `/api/system/tenant` |
| 工作流 | `/api/system/workflow` |

## 监控

| 模块 | 路径 |
| --- | --- |
| 登录/操作日志 | `/api/monitor/login-log`、`/api/monitor/oper-log` |
| 在线用户 | `/api/monitor/online` |
| 缓存 | `/api/monitor/cache/{key}` |
| 看板统计 | `/api/monitor/stats` |
| 定时任务 | `/api/monitor/job`、`/api/monitor/job/log` |
| 服务器监控 | `/api/monitor/server` |
| SQL 监控 | `/api/monitor/sql-log` |

## AI 与通用

| 模块 | 路径 |
| --- | --- |
| AI 配置/任务/回调 | `/api/ai/config`、`/api/ai/tasks`、`/api/ai/callback/task` |
| 文件上传 | `/api/common/upload` |

Python 服务接口：

- `GET /health`
- `GET /api/v1/ping`
- `POST /api/v1/tasks`
- `GET /api/v1/tasks/{task_id}`
- `POST /api/v1/tasks/{task_id}/retry`
- `GET /api/v1/metrics`

在线文档：`http://localhost:8080/doc.html`、`http://localhost:8000/docs`。
OpenAPI JSON：`http://localhost:8080/v3/api-docs`，刷新脚本见 `scripts/fetch-openapi.ps1`。

