# 数据库设计

Flyway 脚本是唯一事实来源，位于 `backend/src/main/resources/db/migration/`，当前版本 V1-V62。

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
| V23 | 工作流条件/并行/表单与超时提醒 |
| V24 | 文件对象存储 key |
| V25 | TOTP 密钥列扩容以支持加密存储 |
| V26 | 关联表补齐 `created_at/updated_at` |
| V27 | 登录日志、操作日志、工作流、AI 任务、公告、定时任务高频查询索引 |
| V28 | 所有自增主键统一为 `BIGINT UNSIGNED` |
| V29 | 普通用户角色移除看板权限，授权通知公告菜单 |
| V30 | 文件存储增强：`content_type/category/sha256/扫描状态/逻辑删除` |
| V31 | 消息中心：`sys_message`、`sys_message_read` 与消息待办、铃铛聚合 |
| V32 | Warm-Flow 工作流引擎表（`flow_definition/flow_node/flow_skip/flow_ins_order/flow_task` 等） |
| V33 | `sys_user` 用户名唯一键按租户隔离（`uk_sys_user_tenant_username`） |
| V34 | 字段级审计日志：`sys_field_audit_log` |
| V35 | 组件演示菜单种子数据 |
| V36 | 设备模块：`sys_device` |
| V37 | 报表模块菜单 |
| V38 | 消息通道：`sys_channel_config`、`sys_channel_log` |
| V39 | AI 增强：`ai_prompt_template`、`ai_knowledge_base`、`ai_knowledge_doc` |
| V40 | OAuth/SSO：`sys_oauth_config`、`sys_user_oauth`、`sys_oauth_client` |
| V41 | MCP Server 配置：`sys_mcp_server` |
| V42 | 可靠投递发件箱：`sys_outbox` |
| V43 | 数据权限规则可配置：`sys_data_permission` |
| V44 | 消息模板：`sys_message_template`；`sys_message` 增加 `content_type`（TEXT/HTML 富文本） |
| V45 | API 资源级权限：`sys_api_perm`（method+path → 所需权限编码），URL 层从"仅认证"升级为"认证+资源权限" |
| V46 | 共享字典：`sys_dict_type` 增加 `is_shared`（tenant_id=0 全局一份）+ `common_status` 种子共享字典 |
| V47 | 报表定义：`report_definition`（自定义 SQL 报表，执行期只读守卫 + 行数上限） |
| V48 | 物模型/遥测：`device_thing_model`、`device_telemetry` |
| V49 | 导入导出中心：`import_export_template`、`import_export_job` |
| V50 | 表单引擎：`form_definition`、`form_instance` |
| V51 | 批次4 菜单权限种子补齐（报表/设备/导入导出/表单） |
| V52 | 导入导出任务查询字段与索引补充 |
| V53 | 大屏模板：`screen_template` 与菜单权限 |
| V54 | AI 任务错误类型字段 |
| V55 | AI 任务重试字段 |
| V56 | 共享字典菜单父级修复 |
| V57 | OAuth 密钥加密存储（`SecretCipher` 对称加密列迁移） |
| V58 | 可靠投递发件箱处理状态字段 |
| V59 | 审计日志数据权限注册：`sys_audit_log`/`sys_field_audit_log` → `sys_data_permission` |
| V60 | 菜单 id 动态化：`uk_sys_menu_perm` 唯一索引，菜单业务定位键由「数字 id 区间」改为「perm 唯一键」 |
| V61 | 业务表数据权限注册：`form_instance`/`import_export_job` → `sys_data_permission`（表单提交记录按提交人、导入导出任务按创建人过滤） |
| V62 | 渠道发送日志 PII 落库加固：`sys_channel_log` 的 `target`/`content` 扩列以容纳加密密文（VARCHAR→TEXT） |

## 表清单

### 系统与权限

| 表 | 说明 |
| --- | --- |
| `sys_user` | 用户，含部门、租户、审计字段、乐观锁 |
| `sys_role` | 角色，含数据权限范围、审计字段、乐观锁 |
| `sys_menu` | 菜单与按钮权限 |
| `sys_api_perm` | API 资源权限映射：method+path 模式 → 所需权限编码，URL 层资源级校验 |
| `sys_data_permission` | 数据权限规则（表 → 行级表达式列映射），可配置热加载 |
| `sys_user_role` | 用户角色 |
| `sys_role_menu` | 角色菜单 |
| `sys_role_dept` | 角色自定义数据权限部门 |
| `sys_dept` | 部门，含审计字段、乐观锁 |
| `sys_post` | 岗位，含审计字段、乐观锁 |
| `sys_user_post` | 用户岗位 |

### 基础数据

| 表 | 说明 |
| --- | --- |
| `sys_dict_type` | 字典类型，含审计字段、乐观锁；`is_shared=1` 表示共享字典（tenant_id=0 全局一份） |
| `sys_dict_data` | 字典数据，含审计字段、乐观锁；共享类型数据在 tenant_id=0，租户私有数据按 dict_value 覆盖共享层 |
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
| `sys_message` | 系统消息，含租户、类型、标题、内容（TEXT/HTML 富文本）、已读状态 |
| `sys_message_read` | 消息已读 |
| `sys_message_template` | 消息模板：`${key}` 占位符 + TEXT/HTML 内容类型，按模板渲染发送 |
| `sys_channel_config` | 消息渠道配置（邮件/短信/钉钉/企微） |
| `sys_channel_log` | 渠道发送记录（含重试失败的错误信息） |
| `sys_workflow` | 工作流实例，含流程定义、当前节点、审计字段、乐观锁 |
| `sys_workflow_log` | 工作流审批日志 |
| `sys_process_def` | 流程定义，含租户、唯一流程标识 |
| `sys_process_node` | 流程节点，含审批角色 |
| Warm-Flow 引擎表 | `flow_definition/flow_node/flow_skip/flow_ins_order/flow_task` 等流程引擎持久化表 |

### AI

| 表 | 说明 |
| --- | --- |
| `ai_service_config` | AI 服务配置 |
| `ai_task` | AI 任务 |
| `ai_task_result` | AI 任务结果 |

## 关键设计

- 用户、角色、部门、岗位使用逻辑删除。
- `created_by/updated_by` 由 `MyMetaObjectHandler` 自动填充，核心业务表带 `version` 乐观锁并由 `OptimisticLockerInnerInterceptor` 校验。
- 租户隔离：`sys_user`、`sys_file`、`sys_notice`、`sys_message`、`sys_job`、`sys_workflow`、日志与 AI 业务表均带 `tenant_id`，MyBatis-Plus 租户拦截器按当前 `TenantContext` 自动追加条件；自定义 `<script>` Mapper 方法通过 `@InterceptorIgnore(tenantLine = "true")` 处理。
- OAuth 三表隔离模型（R4-1.22，刻意不进租户白名单）：`sys_oauth_config` 为**平台级配置**，登录/授权在匿名上下文按 provider 全局解析（`OauthLoginService.requireEnabled`），注入租户条件会恒落租户 1 使非平台租户配置查不到——因此仅租户 1（平台）管理员可管理，服务层 `requirePlatformTenant` 守卫；`sys_oauth_client` 为**租户私有**（SSO 应用），page 过滤当前租户、create 落当前租户、update/status/delete 校验归属，且 `client_id` 跨租户全局唯一（SSO 匿名链路按 client_id `selectOne`，重名抛 `TooManyResultsException`）；`sys_user_oauth` 所有访问均已显式按租户过滤，无需额外约束。
- 用户名唯一性按租户隔离：`sys_user` 使用 `uk_sys_user_tenant_username(tenant_id, username)`，多租户下各租户可存在同名账号。
- 数据权限：角色支持全部数据、本部门、本部门及以下、自定义部门四种范围，查询时由 `DataScopeHelper` 统一注入。
- 数据权限 AOP：`@DataScope` 注解 + MyBatis SQL 拦截器，受控表与关联列从 `sys_data_permission` 配置表读取（可热加载）。当前受控表：`sys_user`（按用户 ID）、`ai_task`（按创建人）、`sys_oper_log`/`sys_login_log`、`sys_audit_log`/`sys_field_audit_log`（日志类）、`form_instance`（按提交人 submitter_id）、`import_export_job`（按创建人 created_by）。**语义矩阵**：`report_definition`/`form_definition`/`screen_template` 是租户内全局共享的配置类数据（由租户隔离 + builtin/status 业务条件控制可见性），`sys_notice`/`sys_message` 公告全员可见、消息按接收人定向分发，`sys_channel_config`/`ai_service_config` 等配置表仅管理员管理——以上**明确不施加**行级过滤，靠业务查询条件或租户隔离兜底。
- 权限、字典、参数支持 Redis 缓存，权限变更自动失效缓存。
- 文件元数据统一写入 `sys_file`，存储后端支持本地目录与 MinIO。
- SQL 日志阈值由 `SQL_LOG_THRESHOLD_MS` 控制，默认 50ms。
- 定时任务使用 Quartz JDBC 存储，任务定义与执行日志持久化。
- 工作流引擎包含流程定义、审批节点、待办任务与审批日志，支持按角色流转。
- 通知实时推送使用 SSE，告警规则由定时任务检查并写入通知公告。
- SSE 使用一次性 Ticket 建立连接，避免 Token 出现在 URL。
- 告警支持 `INFO/WARNING/CRITICAL` 分级、静默期与 Webhook 推送。
- 工作流支持发起人撤回和审批转办。
- 工作流节点支持条件表达式、并行审批、表单数据和超时提醒。
- 租户支持用户上限、存储配额与管理员绑定。
- AI 服务支持每日任务限额，非管理员查看用户敏感字段自动脱敏。
- 上传文件通过签名 Token 访问，TOTP 密钥加密存储。
- 所有数据库变更必须新增 Flyway 脚本，禁止手工改生产库。
- 高频查询字段采用组合索引，索引命名统一为 `idx_表名_字段名`。
- 菜单迁移规范（R4-1.36 起）：`sys_menu.id` 一律由数据库自增分配，**禁止**在迁移脚本中硬编码 id 区间；新增菜单的守卫用 `WHERE perm='...'`、给角色授权用 `INSERT INTO sys_role_menu (role_id, menu_id) SELECT 角色id, id FROM sys_menu WHERE perm='...'` 动态解析。`perm` 是菜单业务定位键（`uk_sys_menu_perm` 唯一索引约束），目录/菜单行（perm 为 NULL）不受唯一约束。
