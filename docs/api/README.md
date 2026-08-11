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
| `1006` | 用户名已存在 |
| `1007` | 角色编码已存在 |
| `1008` | 存在下级部门，不能删除 |
| `1009` | 岗位编码已存在 |
| `1010` | AI 服务不可用 |
| `1011` | AI 服务未启用或不存在 |
| `1012` | 字典类型已存在 |
| `1013` | 参数键名已存在 |
| `1014` | 登录过于频繁或账号已锁定 |
| `1015` | 需要两步验证 |
| `1016` | 租户用户数量已达上限 |
| `1017` | AI 任务已达每日上限 |
| `1018` | 租户存储空间不足 |
| `1019` | 验证码错误 |
| `1020` | 动态验证码错误 |
| `1021` | AI 回调签名无效 |
| `1022` | 非法回调状态 |
| `1023` | 当前流程已结束 |
| `1024` | 流程定义不可用 |
| `1025` | 流程定义没有可进入的起始节点 |
| `1026` | 文件未通过安全检查 |
| `1027` | 病毒扫描服务不可用 |
| `1028` | 设备编码已存在 |
| `1029` | Prompt 模板编码已存在 |
| `1030` | 请勿重复提交 |
| `1031` | 系统繁忙，请稍后重试 |
| `1032` | 报表编码已存在 |
| `1033` | 报表数据源仅支持只读查询 |
| `1034` | 物模型类型已存在 |
| `1035` | 导入导出模板编码已存在 |
| `1036` | 表单编码已存在 |
| `1037` | 表单定义无效 |
| `1038` | 表单数据校验不通过 |
| `429` | 请求过于频繁 |
| `500` | 系统繁忙，请稍后重试 |

分页接口统一返回 `PageResult`，包含 `list/total/pageNum/pageSize`。除登录等公开接口外，请求头需携带 `Authorization: Bearer <accessToken>`。

接口同时提供版本化前缀 `/api/v1/**`，与 `/api/**` 行为一致；新客户端建议使用 `/api/v1/**`。

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
| GET/POST | `/api/auth/totp/*` | 两步验证状态、开启、启用、关闭 |

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
| 通知推送 Ticket | `/api/system/notice/ticket` | 获取一次性 SSE Ticket |
| 消息中心 | `/api/system/message` | 分页、最新消息、详情、未读数、已读/全部已读、发送 |
| 消息待办 | `/api/system/message/todos` | 消息待办任务（对接 Warm-Flow） |
| 消息聚合 | `/api/system/message/feed` | 铃铛聚合：消息 + 公告统一推送（含 bizType/bizId 深链） |
| 租户 | `/api/system/tenant` | 租户 CRUD |
| 租户内用户 | `/api/system/tenant/{tenantId}/users` | 租户管理员候选（当前租户内用户） |
| 管理员候选 | `/api/system/tenant/admin-candidates` | 可担任租户管理员的跨租户候选 |
| 文件管理 | `/api/system/file` | 文件分页、删除 |
| 工作流引擎 | `/api/system/workflow-engine` | 发起、实例分页、待办、通过、拒绝、撤回、转办、审批日志 |
| 工作流详情 | `/api/system/workflow-engine/{id}` | 详情出参：头部信息 + 表单回显(formData) + 完整流程轨迹(trace) + 当前待办节点 |
| 流程定义 | `/api/system/workflow-engine/def` | 流程定义 CRUD、发布/取消发布、节点查询、定义选项 |
| 当前节点 | `/api/system/workflow-engine/{id}/nodes` | 当前待办节点查询 |

## 业务模块（批次4）

### 报表定义化

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/report/definition/page` | 报表定义分页查询 |
| POST | `/api/report/definition` | 新增报表定义（SQL 仅允许只读查询，校验后入库） |
| PUT | `/api/report/definition` | 编辑报表定义 |
| DELETE | `/api/report/definition/{id}` | 删除报表定义 |
| GET | `/api/report/definition/{id}` | 报表定义详情 |
| POST | `/api/report/definition/{id}/execute` | 执行报表查询（校验 SQL 只读，返回数据行） |

### 设备物模型与遥测

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/device/thing-model/page` | 物模型分页查询 |
| GET | `/api/device/thing-model/{id}` | 物模型详情 |
| GET | `/api/device/thing-model/{id}/schema` | 物模型完整结构（属性/事件/服务三要素） |
| POST | `/api/device/thing-model` | 新增物模型 |
| PUT | `/api/device/thing-model` | 编辑物模型 |
| DELETE | `/api/device/thing-model/{id}` | 删除物模型 |
| POST | `/api/device/telemetry/report` | 设备遥测上报（批量，按物模型校验属性） |
| GET | `/api/device/telemetry/latest` | 设备最新遥测快照 |
| GET | `/api/device/telemetry/history` | 设备遥测历史分页 |

### 导入导出中心

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/import-export/template/page` | 导入导出模板分页查询 |
| POST | `/api/import-export/template` | 新增模板（按实体配置列映射与校验规则） |
| PUT | `/api/import-export/template` | 编辑模板 |
| DELETE | `/api/import-export/template/{id}` | 删除模板 |
| POST | `/api/import-export/job/import` | 创建导入任务（模板 + 文件，异步执行） |
| POST | `/api/import-export/job/export` | 创建导出任务（模板 + 查询条件，异步执行） |
| GET | `/api/import-export/job/page` | 导入导出任务分页查询 |
| GET | `/api/import-export/job/{id}` | 任务详情（进度、成功/失败数、错误信息） |
| GET | `/api/import-export/job/{id}/download` | 下载任务结果文件（导出成品 / 导入失败明细） |

导入导出通过 `ImportExportHandler` SPI 扩展实体；内置 `dict-data`（字典数据）处理器。

### 低代码表单引擎

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/form/definition/page` | 表单定义分页查询 |
| GET | `/api/form/definition/{id}` | 表单定义详情 |
| GET | `/api/form/definition/{id}/schema` | 已发布表单的渲染结构（fields + layout） |
| POST | `/api/form/definition` | 新增表单定义（schema 校验） |
| PUT | `/api/form/definition` | 编辑表单定义（乐观锁） |
| PUT | `/api/form/definition/{id}/publish` | 发布表单（草稿 → 已发布） |
| DELETE | `/api/form/definition/{id}` | 删除表单定义 |
| POST | `/api/form/instance/submit` | 提交表单（校验已发布定义 + 字段规则） |
| GET | `/api/form/instance/page` | 表单实例分页查询 |
| GET | `/api/form/instance/{id}` | 表单实例详情 |
| PUT | `/api/form/instance/{id}/status` | 表单审批流转（SUBMITTED → APPROVED/REJECTED） |

## 监控

| 模块 | 方法/路径 | 说明 |
| --- | --- | --- |
| 登录日志 | `/api/monitor/login-log` | 分页查询 |
| 操作日志 | `/api/monitor/oper-log` | 分页查询 |
| 在线用户 | `/api/monitor/online` | 在线用户列表、强制下线 |
| 缓存 | `/api/monitor/cache/{key}` | 查询、删除缓存 |
| 看板统计 | `/api/monitor/stats` | 用户/部门/任务/日志等统计 |
| 定时任务 | `/api/monitor/job` | 任务 CRUD、状态启停、手动执行 |
| 任务日志 | `/api/monitor/job/log` | 定时任务执行日志（`JobLogVo` 类型契约） |
| 服务器监控 | `/api/monitor/server` | CPU、内存、磁盘、JVM 等指标 |
| SQL 监控 | `/api/monitor/sql-log` | 慢 SQL 与 SQL 执行日志（`SqlLogVo` 类型契约） |
| 告警规则 | `/api/monitor/alert-rule` | 告警规则 CRUD、立即检查 |
| 审计日志 | `/api/monitor/audit-log` | 审计日志分页查询 |

## AI 与通用

| 模块 | 方法/路径 | 说明 |
| --- | --- | --- |
| AI 配置 | `/api/ai/config` | 查询、编辑 AI 服务配置 |
| AI 任务 | `/api/ai/tasks` | 创建、分页查询、详情、取消 |
| AI 实时推送 | `/api/ai/tasks/{id}/stream` | SSE 实时任务状态推送 |
| AI 推送 Ticket | `/api/ai/ticket` | 获取一次性 SSE Ticket |
| AI 回调 | `/api/ai/callback/task` | Java 接收 Python 服务回调，带 Token 校验与幂等 |
| 文件上传 | `/api/common/upload` | 本地或 MinIO 上传，20MB 上限 |
| 文件访问 Token | `/api/common/file-token` | 获取上传文件签名访问 Token |

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

接口契约以运行时 OpenAPI 为准，CI 会在每次提交时执行后端测试与覆盖率门槛、前端 lint/测试/构建、AI 测试与规范检查。

