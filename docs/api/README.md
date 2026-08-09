# 接口文档

后端接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "uuid"
}
```

`code=0` 表示成功；`401` 未登录、`403` 无权限、`1001` 参数错误、`1002` 数据不存在、`1010` AI 服务不可用。

## 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 退出登录 |
| GET | `/api/auth/me` | 当前用户、角色、权限、菜单树 |
| PUT | `/api/auth/password` | 修改密码 |

登录请求示例：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

## 系统管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/system/user`、`/api/system/user/{id}` | 用户管理 |
| PUT | `/api/system/user/{id}/status` | 修改用户状态 |
| PUT | `/api/system/user/{id}/roles` | 分配角色 |
| GET/POST/PUT/DELETE | `/api/system/role`、`/api/system/role/{id}` | 角色管理 |
| PUT | `/api/system/role/{id}/menus` | 分配菜单权限 |
| GET/POST/PUT/DELETE | `/api/system/menu`、`/api/system/menu/tree` | 菜单管理 |
| GET/POST/PUT/DELETE | `/api/system/dept`、`/api/system/dept/tree` | 部门管理 |
| GET/POST/PUT/DELETE | `/api/system/post`、`/api/system/post/options` | 岗位管理 |
| GET/POST/PUT/DELETE | `/api/system/dict/type`、`/api/system/dict/data` | 字典管理 |
| GET/POST/PUT/DELETE | `/api/system/config` | 参数配置 |
| GET/POST | `/api/system/user/export`、`/api/system/user/import` | 用户 Excel 导入导出 |
| POST | `/api/common/upload` | 文件上传 |
| GET/POST/PUT/DELETE | `/api/system/notice`、`/api/system/notice/latest` | 通知公告 |

## 监控接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/monitor/login-log` | 登录日志 |
| GET | `/api/monitor/oper-log` | 操作日志 |
| GET/DELETE | `/api/monitor/online`、`/api/monitor/online/{tokenId}` | 在线用户与强制下线 |
| DELETE | `/api/monitor/cache/{key}` | 删除缓存 |
| GET | `/api/monitor/stats` | 看板统计 |
| GET/POST/PUT/DELETE | `/api/monitor/job`、`/api/monitor/job/{id}` | 定时任务管理 |
| GET | `/api/monitor/job/log` | 定时任务日志 |
| GET | `/api/monitor/server` | 服务器监控 |
| GET | `/api/monitor/sql-log` | SQL 监控 |

## AI 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/PUT | `/api/ai/config`、`/api/ai/config/{id}` | AI 服务配置 |
| POST/GET/DELETE | `/api/ai/tasks`、`/api/ai/tasks/{id}` | AI 任务管理 |
| POST | `/api/ai/callback/task` | Python 回调，需 `X-Ai-Service-Token` |

创建 AI 任务示例：

```json
{
  "bizType": "text_summary",
  "serviceCode": "default",
  "params": {
    "content": "需要分析的文本",
    "max_length": 200
  }
}
```

## Python 服务接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | 健康检查 |
| GET | `/api/v1/ping` | Java 连通性探测 |
| POST | `/api/v1/tasks` | 创建 AI 推理任务 |
| GET | `/api/v1/tasks/{task_id}` | 查询任务状态 |
| POST | `/api/v1/tasks/{task_id}/retry` | 手动重试 |

在线文档：`http://localhost:8080/doc.html`、`http://localhost:8000/docs`。

