# 接口文档

## 统一约定

Java 后端统一响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "uuid"
}
```

常见错误码：

| code | 说明 |
| --- | --- |
| `0` | 成功 |
| `401` | 未登录或登录已过期 |
| `403` | 无权限访问 |
| `1001` | 参数错误 |
| `1002` | 数据不存在 |
| `1003` | 用户名或密码错误 |
| `1004` | 账号已被禁用 |
| `1005` | 原密码错误 |
| `500` | 系统繁忙，请稍后重试 |

分页接口统一返回 `PageResult`，包含 `list/total/pageNum/pageSize`。除登录等公开接口外，请求头需携带 `Authorization: Bearer <accessToken>`。

## 认证与个人中心

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/auth/login-config` | 登录配置（验证码开关） |
| GET | `/api/auth/captcha` | 图形验证码 |
| POST | `/api/auth/login` | 登录，返回 accessToken/refreshToken |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 退出登录 |
| GET | `/api/auth/me` | 当前用户、权限、菜单树 |
| PUT | `/api/auth/password` | 修改密码 |
| PUT | `/api/auth/profile` | 编辑个人资料（昵称、邮箱、手机号、头像等） |

## 系统管理

| 模块 | 方法/路径 | 说明 |
| --- | --- | --- |
| 用户 | `/api/system/user` | 分页查询、新增、编辑、删除、状态、角色授权 |
| 用户导入导出 | `/api/system/user/export`、`/api/system/user/import` | Excel 导入导出 |
| 角色 | `/api/system/role` | CRUD、角色选项、菜单授权、数据权限 |
| 菜单 | `/api/system/menu/tree` | 菜单与按钮权限树，含新增、编辑、删除 |
| 部门 | `/api/system/dept/tree` | 部门树，含新增、编辑、删除 |
| 岗位 | `/api/system/post` | CRUD、岗位选项 |
| 字典 | `/api/system/dict/type`、`/api/system/dict/data` | 字典类型与数据，`/data/type/{dictType}` 获取字典项 |
| 参数 | `/api/system/config` | 参数配置 CRUD |
| 通知公告 | `/api/system/notice` | 公告 CRUD、最新公告、未读数、单条已读、全部已读 |
| 通知实时推送 | `/api/system/notice/stream` | SSE 实时通知流 |
| 租户 | `/api/system/tenant` | 租户 CRUD |
| 文件管理 | `/api/system/file` | 文件分页、删除 |
| 工作流 | `/api/system/workflow` | 发起、分页、待办任务、通过、拒绝、审批日志 |
| 流程定义 | `/api/system/workflow/def` | 流程定义 CRUD、节点查询 |

## 监控

| 模块 | 方法/路径 | 说明 |
| --- | --- | --- |
| 登录日志 | `/api/monitor/login-log` | 分页查询 |
| 操作日志 | `/api/monitor/oper-log` | 分页查询 |
| 在线用户 | `/api/monitor/online` | 在线用户列表、强制下线 |
| 缓存 | `/api/monitor/cache/{key}` | 查询、删除缓存 |
| 看板统计 | `/api/monitor/stats` | 用户/部门/任务/日志等统计 |
| 定时任务 | `/api/monitor/job` | 任务 CRUD、状态启停、手动执行 |
| 任务日志 | `/api/monitor/job/log` | 定时任务执行日志 |
| 服务器监控 | `/api/monitor/server` | CPU、内存、磁盘、JVM 等指标 |
| SQL 监控 | `/api/monitor/sql-log` | 慢 SQL 与 SQL 执行日志 |
| 告警规则 | `/api/monitor/alert-rule` | 告警规则 CRUD、立即检查 |

## AI 与通用

| 模块 | 方法/路径 | 说明 |
| --- | --- | --- |
| AI 配置 | `/api/ai/config` | 查询、编辑 AI 服务配置 |
| AI 任务 | `/api/ai/tasks` | 创建、分页查询、详情、取消 |
| AI 回调 | `/api/ai/callback/task` | Java 接收 Python 服务回调，带 Token 校验与幂等 |
| 文件上传 | `/api/common/upload` | 本地或 MinIO 上传，20MB 上限 |

Python 服务接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | 健康检查 |
| GET | `/api/v1/ping` | 连通性测试 |
| POST | `/api/v1/tasks` | 创建异步任务 |
| GET | `/api/v1/tasks/{task_id}` | 查询任务状态与结果 |
| POST | `/api/v1/tasks/{task_id}/retry` | 失败重试 |
| GET | `/api/v1/metrics` | Prometheus 指标 |

## 在线文档

- Java 后端：`http://localhost:8080/doc.html`
- Python 服务：`http://localhost:8000/docs`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 刷新本地契约：`scripts/fetch-openapi.ps1`

接口契约以运行时 OpenAPI 为准，CI 会在每次提交时执行后端测试、前端测试与构建、AI 测试与规范检查。

