# API 接口约定

本文定义 Java 后端（`backend`）HTTP API 的**通用约定**：请求前缀、认证方式、统一
响应与错误码、分页、限流、SSE 认证，以及 OpenAPI 契约的获取方式。它是给“新加入的
下游开发者 / 前端 / 第三方集成方”的导读，**不是完整接口清单**——单接口路径、参数与
出参以运行时 OpenAPI 契约为准（见文末）。

## 请求前缀与版本化

- 控制器统一挂在 `/api/**` 下（如 `/api/auth/login`、`/api/system/user`）。
- `/api/v1/**` 与 `/api/**` 等价：`ApiVersionFilter`（`common/ApiVersionFilter.java`）
  在请求入口把 `/api/v1/` 前缀改写为 `/api/` 再路由。新客户端建议使用
  `/api/v1/**`，旧地址持续可用。
- 前缀不是模块名一部分的静态资源（`/uploads`、`/files/**` 文件访问等）不在本约定内。

## 认证方式

### Access Token

除登录、验证码、AI 回调等公开端点外，受保护接口需携带请求头：

```
Authorization: Bearer <accessToken>
```

- `accessToken` 为 JWT，有效期默认 120 分钟（`JWT_ACCESS_EXPIRE_MINUTES`），
  服务端同时在 Redis 中维护会话；登出/改密/停用会吊销相关令牌。
- 未携带或过期返回 HTTP 401，响应体为统一 `Result`（见下）。

### Refresh Token（httpOnly Cookie）

- 登录成功时服务端把 `refreshToken` 写入 **httpOnly Cookie**（默认名
  `admin_refresh_token`，`SameSite=Lax`；生产 HTTPS 下 `Secure` 由
  `REFRESH_COOKIE_SECURE` 控制）。前端脚本无法读取该 Cookie，降低 XSS 窃取长期
  凭证的风险；登录响应体的 `refreshToken` 字段保留供脚本/兼容客户端使用。
- 刷新：`POST /api/auth/refresh`。服务端**优先读取 httpOnly Cookie**；无 Cookie 时
  回退请求体 `refreshToken`（兼容旧客户端/脚本）。
- 登出：`POST /api/auth/logout` 同时清除 refresh Cookie。
- Cookie 名/路径/SameSite/Secure 均可通过 `app.security.refresh-*` 配置（见
  `backend/src/main/resources/application.yml`）。

### 两步验证与口令策略

- TOTP：`/api/auth/totp/*` 提供状态查询、开启（`setup`）、启用、关闭。
- 强制改密：生产配置 `app.security.force-password-change` 开启时，默认口令首登或
  密码过期（`password-expire-days`，默认 90 天）后登录响应携带
  `mustChangePassword=true`，客户端须引导用户先改密。
- 密码相关端点：`PUT /api/auth/password`（修改密码）、`PUT /api/auth/profile`
  （个人资料）。

### SSE 流式接口的认证

`EventSource`/浏览器无法为 SSE 请求附加 `Authorization` 头，因此流式端点采用
**一次性 Ticket** 模式：

1. `GET /api/ai/ticket`、`GET /api/system/notice/ticket` 等取票（需正常鉴权）；
2. 携带 `?ticket=...` 连接流式端点（如 `/api/ai/tasks/{id}/stream`、
   `/api/system/notice/stream`）。

票据即身份（服务端换取 userId），并有每用户并发连接上限与心跳保活，防止单账号
无限开流耗尽资源。

## 统一响应结构

绝大多数接口返回统一信封 `Result<T>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "uuid"
}
```

- `requestId`：链路标识。服务端接受入站 `X-Request-Id`，缺省自动生成 UUID，
  回显于响应体并在响应头 `X-Request-Id` 透出（日志中 `[%X{requestId}]` 一致贯穿）。
- **HTTP 状态与业务码的关系**：业务校验类错误返回 **HTTP 200 + `code != 0`**
  （如参数错误 1001、数据不存在 1002），前端按 `Result.code` 统一处理；HTTP 层
  语义错误（未认证 401、无权限 403、资源不存在 404、方法不支持 405、超限 413/429、
  服务端 500 等）直接使用对应状态码，响应体仍为同一 `Result` 结构。完整映射以
  `GlobalExceptionHandler` 为准。

## 错误码约定

错误码集中在后端枚举 `ResultCode`（源码：
`backend/src/main/java/cn/admin/scaffold/common/ResultCode.java`），分两类：

- **HTTP 语义码**（与 HTTP 状态一致）：`401` 未登录或已过期、`403` 无权限、
  `404` 资源不存在、`405` 方法不支持、`413` 上传超限、`415` 媒体类型不支持、
  `429` 请求过于频繁、`500` 系统繁忙。
- **业务码**（`1001` 起，HTTP 200 + 业务码返回）常用分组：

| 分组 | code 段 | 典型含义 |
| --- | --- | --- |
| 参数与数据 | 1001 / 1002 | 参数错误 / 数据不存在 |
| 账号 | 1003–1006 | 用户名或密码错误 / 账号禁用 / 原密码错误 / 用户名已存在 |
| 唯一性冲突 | 1007、1009、1012–1013、1028–1029、1032、1034–1036、1039 | 角色/岗位/字典/参数/设备/Prompt/报表/物模型/模板/表单/大屏等编码或类型已存在 |
| 组织与租户 | 1008、1016、1018 | 存在下级部门不能删除 / 租户用户数上限 / 存储空间不足 |
| AI 服务 | 1010–1011、1017、1021–1022 | AI 服务不可用或未启用 / 每日上限 / 回调签名无效 / 非法回调状态 |
| 认证与风控 | 1014、1015、1019–1020、1040 | 登录过于频繁或账号锁定 / 需两步验证 / 图形或动态验证码错误 / 跨租户同名需指定租户 |
| 工作流 | 1023–1025 | 流程已结束 / 定义不可用 / 无起始节点 |
| 文件安全 | 1026–1027 | 未通过安全检查 / 病毒扫描不可用 |
| 通用 | 1030–1031 | 请勿重复提交 / 系统繁忙（分布式锁超时） |
| 报表 / 表单 | 1033、1037–1038 | 数据源仅支持只读 / 表单定义无效 / 表单数据校验不通过 |

> 维护约定：新增错误码必须同步修改 `ResultCode` 枚举与前端语言包
> `frontend/src/locales/zh-CN.ts`、`frontend/src/locales/en-US.ts`；历史码不删除、
> 不改变语义（错误码分组规约见 `docs/architecture-conventions.md`）。

## 分页

分页接口统一返回 `PageResult`，字段为：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 20
}
```

- 分页请求参数一般为 `pageNum` / `pageSize`（具体以接口为准）；
- 结果集合字段是 **`records`**（不是 `list`），与后端 `PageResult` 类一一对应。

## 限流

- 全局接口限流：注册在 `/api/**` 上的拦截器（`common/ApiRateLimitInterceptor.java`）
  按**身份桶**计数——已登录按 `user:{userId}`，匿名按 socket 源地址 `ip:{remoteAddr}`；
  每身份每分钟上限默认 300（`API_RATE_LIMIT_PER_MINUTE`），超限返回 HTTP 429 +
  `code=429`。
- 限流身份**从不信任 `X-Forwarded-For`**（客户端可控）。默认
  `forward-headers-strategy=none` 时匿名按反代出口 IP 计数（全站共享桶，更严不更松）；
  只有部署在会正确覆盖/重写该头的可信反向代理之后，才应显式注入
  `SERVER_FORWARD_HEADERS_STRATEGY`。
- 登录接口另有失败阶梯锁定（Redis `login:fail:*`，键含租户维度），命中返回
  `code=1014`。
- SSE/长连接有每用户并发上限与心跳（见 `application.yml` `app.notice-sse-*` /
  `app.ai-task-sse-*` / `app.screen.*` / `app.websocket.*` 等配置）。

## 模块导读（以运行时 OpenAPI 为准）

下表只列模块入口前缀，帮助定位；具体端点见运行时契约。

| 模块 | 前缀 | 覆盖能力 |
| --- | --- | --- |
| 认证与个人中心 | `/api/auth`、`/api/auth/oauth` | 登录配置/验证码、登录、刷新、登出、当前用户、改密、资料、TOTP、OAuth2 登录 |
| 系统管理 | `/api/system` | user、role、menu、dept、post、dict、config、notice、message、file、tenant、数据权限、API 权限 |
| 工作流 | `/api/system/workflow-engine`（含 `/def`） | 流程定义/发布、发起、待办、审批、驳回/撤回/转办、日志、节点 |
| 通知渠道与消息模板 | `/api/notice/channel`、`/api/notice/message-template` | 通知渠道配置、消息模板 |
| 监控 | `/api/monitor` | 登录/操作日志、在线用户、缓存、看板统计、定时任务（`/job`、`/job/log`）、服务器监控、SQL 日志、字段审计、告警规则 |
| AI 管理 | `/api/ai` | 服务配置、任务（创建/分页/详情/取消/重试/SSE 实时）、Prompt、知识库、对话 |
| 通用与文件 | `/api/common`、`/api/common/file/chunk`、`/api/common/file/presign` | 上传、文件访问令牌、存储配额、分块上传、预签名直传；文件内容经受保护路径访问（`/files/{id}`、`/api/system/file/{id}/download`） |
| 报表与大屏 | `/api/report`（`/definition`、`/screen/template`） | 报表定义与执行、大屏模板 |
| 设备 | `/api/device`（`/thing-model`、`/telemetry`） | 设备、物模型、遥测 |
| 导入导出 | `/api/import-export`（`/template`、`/job`） | 模板、异步导入导出任务与结果下载 |
| 表单引擎 | `/api/form`（`/definition`、`/instance`） | 表单定义/发布、实例提交/审批 |
| MCP | `/api/mcp` | MCP 只读工具端点 |

> 反向代理注意：文件上传体默认上限 20MB（`spring.servlet.multipart`），网关
> `proxy-body-size` 需对齐，否则触发 413。

## OpenAPI 契约说明

- Java 后端通过 springdoc + Knife4j 自动生成：在线文档 `http://localhost:8080/doc.html`
  （dev 环境），JSON 契约 `http://localhost:8080/v3/api-docs`；
- Python AI 服务（FastAPI 自动生成）：`http://localhost:8000/docs`；
- 真实契约以**运行时 `/v3/api-docs` 为准**，不要依据本文手写表格做实现；
  契约快照的生成、入库与门禁见 [docs/api/openapi.md](./openapi.md)；
- 刷新本地契约快照：`scripts/fetch-openapi.ps1`。

CI 在每次提交执行后端 `mvn verify`（含测试与 JaCoCo 覆盖率门槛）、前端
lint/test/build、AI 测试与规范检查（详见 `docs/runbook.md` 与 `.github/workflows/ci.yml`）。
