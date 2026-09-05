# 架构与设计规约

本文档是开发规约的权威来源：所有模块与新增代码必须遵守；与代码事实冲突时以本文档 + 对应模块文档为准。

## 分层架构

统一遵循 `Controller -> Service -> Manager -> DAO`：

| 层 | 职责 | 约束 |
| --- | --- | --- |
| Controller | 接收 HTTP 参数、参数校验、组装返回 | 不写业务规则，不直接访问 Mapper |
| Service | 业务逻辑、事务控制、领域规则 | 事务注解只放在 Service 方法上 |
| Manager | 跨服务、第三方 API、外部系统封装 | 外部调用统一收敛到 Manager，便于替换与测试 |
| DAO/Mapper | 数据库访问 | 不包含业务逻辑，SQL 显式列出字段 |

- 外部能力示例：`AiTaskManager`、`AlertWebhookManager`；文件存储通过 `FileStorage` 接口隔离本地/MinIO，配额统一走 `StorageQuotaService`，当前用户提取统一走 `SecurityUtils.tryGetUserId()`。
- 流程审批/驳回入参（`WarmFlowWorkflowActionRequest`）的 `taskId`/`nodeId` 必须保持可空：Service 的 `resolveTaskId` 负责 "taskId → nodeId → 唯一待办回退" 的解析兜底，DTO 不得强制要求前端补齐字段，否则旧客户端/未选节点的调用会直接 400。

## 命名与领域模型

- DO：数据库实体，表字段与 Java 属性映射，统一 `Sys*DO` / `Ai*DO` 命名
- DTO：接口出入参对象，只承载传输数据
- VO：前端展示对象，可包含聚合字段
- BO：Service 内部业务对象，避免与数据库模型耦合
- 类名 `UpperCamelCase`，方法/变量 `lowerCamelCase`，常量 `UPPER_SNAKE_CASE`
- 包名全部小写，`module` 下按 `controller/service/manager/mapper/entity/dto/vo` 分层

## 异常与错误码

- 业务异常统一抛出 `BusinessException`，错误码定义在 `ResultCode`（分组说明见 `docs/api/README.md`，权威以 `ResultCode.java` 为准）
- 禁止 `e.printStackTrace()`，统一使用 SLF4J `log.error/warn`
- 日志与操作日志不得输出密码、Token、API Key、手机号、身份证等敏感字段
- 敏感凭据落库必须加密（`SecretCipher`，AES-256-GCM，"enc:" 前缀）：覆盖 OAuth clientSecret/appSecret、通知渠道 `config_json` 中的密钥字段（SMTP 密码、短信 apiKey、钉钉加签 secret、Webhook headers 的 Authorization/token 等）、AI 服务 `apiKey`、MCP Server `authToken`。回显打码、使用前解密（加密值带 "enc:" 前缀幂等跳过、编辑留空不改）。回显打码与落库加密共用 `LogMaskUtils` 的同一敏感键清单（大小写不敏感匹配），防止两套清单漂移。`@OperLog` 操作日志入参/结果脱敏走同一黑名单：**新增任何凭据字段必须同步加入 `LogMaskUtils.SENSITIVE_FIELDS`**。
- 渠道发送正文/接收目标（`sys_channel_log.content`/`target`）按 PII 加密落库、回显解密、存量明文行 fail-closed 打码；发送类接口的操作日志用 `@OperLog(maskFields = ...)` 声明式对正文/参数整值打码，避免把 `content` 等通用键加入全局敏感键清单误伤公告/通知审计
- 上传类接口（普通上传/分片上传/预签名直传）一律带 `@OperLog`，`MultipartFile` 入参由 `OperLogAspect.filterArgs` 自动降级为元信息快照（originalFilename/size/contentType）——`MultipartFile` 序列化会整读文件进堆并 base64 展开，须避免大文件上传时额外占一份完整文件内存
- 新增错误码时同步维护前端 `zh-CN.ts` / `en-US.ts` 语言包

## 数据访问

- 自定义 SQL 禁止 `SELECT *`，只查询所需字段
- 多租户数据必须带 `tenant_id` 条件，配合数据权限注解使用
- 高频查询字段建立组合索引，索引命名 `idx_表名_字段名`
- 每次结构变更通过 Flyway 迁移，禁止手工改库；只允许新增 `V(n+1)` 文件，禁止修改已发布迁移
- **禁止「逻辑删除 + 业务编码唯一键」组合**：`@TableLogic` 表若同时有 `(tenant_id, 编码)` 唯一键，删除后同名/同编码数据无法重建（exists 检查自动过滤 deleted=0 判定不存在、INSERT 却命中唯一键）。带唯一键的业务表删除路径必须在删除前调用 `UniqueKeyRelease.releaseCode` 释放唯一键（改写编码释放约束）；**新增表禁止出现该组合**；若确需逻辑删除 + 业务编码，唯一键必须包含 `deleted` 且 `deleted` 存删除时间戳（0=未删），并保持实体/删除语义一致
- 分页 pageSize 全局封顶 200（`MybatisPlusConfig.MAX_PAGE_SIZE` + `PaginationInnerInterceptor.setMaxLimit`），新增分页入口不得绕过；内存分页统一走 `PageUtil.fromIndex/toIndex` 钳制 pageNum≤0/pageSize≤0，禁止手写 `(pageNum-1)*pageSize` 下标
- 状态机字段（AI 任务 `ai_task.status` 等）的流转必须用条件 UPDATE：MyBatis-Plus `LambdaUpdateWrapper` 以 `.eq/in(status, 允许前置)` 限定 + `.set(...)`，禁止 "check-then-act 读状态再无条件 updateById"——并发回调/扫描器/取消/重试会互相覆盖终态；影响 0 行视为被并发抢先，静默返回不报错（`handleCallback`/`AiTaskScanner`/`retry`/`cancel`/建单后置 QUEUED 均为此模式）

## 安全与访问控制

- 口令策略（服务端强制）：默认口令创建的账号、被重置口令的账号必须置 `must_change_password`；策略开启时（见 `SecurityProperties`，生产默认开启），未改密或口令过期的用户除白名单端点（改密/登出/刷新等）外一律拦截（`PasswordPolicyEnforcementFilter`）；改密/重置/停用/调整角色会吊销该用户全部会话（access 白名单 + refresh 记录，按用户级集合统一清理）
- 受保护文件路径（`/files/**`、`/uploads/**`）与 URL 层权限匹配，校验前必须与 Spring MVC 同序规范化路径（剥 `;` 矩阵参数 → URL 解码 → 折叠重复斜杠/尾斜杠 → 处理 `/api/v1` 前缀），防止 `;`/编码变体绕过过滤造成 IDOR 或规则漏配（`PathNormalizer` 统一口径；签发与校验共用同一规范化函数）
- URL 层资源权限（`sys_api_perm`，`ApiPermAuthorizationFilter`）：规则未命中默认仅要求已认证，同时输出 warn 日志暴露漏配端点；生产可开启严格模式（未命中规则即拒绝）——新接口必须显式沉淀规则或使用 `@PreAuthorize`
- 文件访问令牌统一经 `GET /api/common/file-token` 按需现取，禁止列表/上传接口缓存签发令牌随数据下发（令牌 TTL 后失效，页面停留超时再访问会 403）；前端取令牌 + origin 拼接统一走 `frontend/src/utils/fileUrl.ts` 的 `withFileToken`/`absoluteFileUrl`，禁止页面手写重复
- 文件令牌签发前必须校验文件归属，防止为其他租户文件签发访问令牌（跨租户文件读取）：`/files/{id}` 走 `FileStorageManager.getOwnedOrThrow`（id + tenant_id），历史 `/uploads/{objectKey}` 走 `getOwnedByLegacyUrlOrThrow`（URL 精确匹配 + tenant_id）
- 本地 Docker 栈 nginx 反代路径集合（`docker/nginx.conf`：`/api/`、`/uploads/`、`/files/`、`/ws/`）必须与 K8s Ingress（`k8s/helm/admin-scaffold/templates/all.yaml`：`/api`、`/files`、`/uploads`、`/ws`）保持一致；上传文件 `contentUrl=/files/{id}` 带 `?token=` 访问。新增受保护路径时两个入口必须同步
- 登录失败锁定键带租户维度（`login:fail:{tenantId}:{username}`），锁定超阈值按 2 的幂指数退避，禁止全局单键——否则未认证者可对任一租户账号制造跨租户 DoS
- 数据权限 `@DataScope` 只约束列表查询；按 id 直查的写路径（update/delete/updateStatus/assignRoles/assignPosts 等）必须单独做单条归属校验：`loadXxxOrThrow` + `checkXxxDataScope`（admin 短路 → `allowedXxxIds()` 为 null 放行 → 目标 id 命中集合否则 403）
- 数据权限 SQL 改写 **fail-closed**：`DataScopeInnerInterceptor` 对受控表只出现在子查询/派生表内层、或语句类型非 PlainSelect（UNION 等）时直接拒绝执行，禁止静默放行——改写失败即抛 FORBIDDEN；自写 SQL/复杂查询不要依赖拦截器，参考 `ReportSqlGuard` 自行加条件
- JWT 过期/签名非法（refresh/logout 重放）应映射 401（`GlobalExceptionHandler` 兜底 JwtException），不得落入兜底 500
- 出站外部地址（告警 Webhook、通用 Webhook 渠道、MCP Server 等）必须过 `SsrfUrlValidator` 双层校验：保存时静态校验（协议仅 http/https、禁 URL 用户信息、拒 localhost/回环/链路本地/站点本地/保留网段 IP 字面量，不发起 DNS）+ 投递/连接前 DNS 复核（任一解析地址落内部/保留网段即拒）——否则服务端可被当作探测内网/云元数据的跳板
- 密码复杂度统一走 `PasswordPolicy.PATTERN`/`MESSAGE` 常量（8-32 位，须含大写/小写/数字/特殊字符四类），DTO `@Pattern` 与 Service 层显式校验共用同一正则；前端 `validation.ts` 的 `PASSWORD_PATTERN` 跨语言无法共享，需人工同步。批量导入的默认密码按产品策略单独处理（不豁免时须满足复杂度）

## 工程与测试

- Service 业务核心必须有单元测试，外部依赖用 Mockito 隔离
- 覆盖阈值由 JaCoCo / Vitest / pytest 覆盖率在 CI 中强制校验（门槛以各模块配置为准）
- 标准 CRUD 用轻量代码生成器（`scripts/crud-gen/`，零依赖 Python）快速产出骨架，复杂业务在生成后手写扩展；生成代码合入前必须通过其自带单测（契约断言保证 api.ts 与 Controller 映射一致）并补齐 i18n/数据权限
- 测试门禁与贡献流程见 `CONTRIBUTING.md`
