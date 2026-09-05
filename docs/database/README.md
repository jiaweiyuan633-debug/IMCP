# 数据库设计与迁移

## 1. 事实来源

Flyway 迁移脚本是数据库结构的唯一事实来源，位于 `backend/src/main/resources/db/migration/`。**本文不固化版本号：当前版本一律以该目录最新 V 文件为准**（核对时最新为 `V63__password_change_policy.sql`，后续新增取 V64 起）。列结构、索引、种子数据以各 V 文件为准；只读本 README 无法替代查阅迁移目录。

运行时行为要点：

- Flyway 在应用启动时自动执行（prod 层 `spring.flyway.enabled=true`、`locations=classpath:db/migration`）。
- `placeholder-replacement: false`：迁移中字面量 `${key}`（如消息模板列注释）不会被替换，不要依赖 Flyway 占位符。
- prod `baseline-on-migrate: false`：禁止对非空库自动打基线；dev/test 允许 baseline（迁移一致性由 Flyway `validate-on-migrate` 默认开启保障）。

## 2. 迁移纪律

1. **只新增 V(n+1)**：新脚本编号 = 当前目录最大版本 +1，文件名形如 `V64__<描述>.sql`；**永不修改已发布的迁移**。Flyway 对已执行脚本做 checksum 校验，改历史脚本会使 checksum 不一致、`migrate` 失败，生产启动即失败。表结构/数据变更一律走新迁移。
2. **禁止手工改生产库**：包括数据修复、加索引——全部写成迁移（含 UPDATE 语句）。
3. **H2/MySQL 双写兼容**：既有迁移为兼容测试库（H2 MySQL 模式）对同一表的多个变更拆成独立 `ALTER TABLE` 语句（见 `V33__tenant_user_unique_key.sql` 注释），新迁移沿用该写法。
4. **菜单/权限迁移规范**（自 `V60__menu_perm_unique_dynamic.sql` 起）：`sys_menu.id` 一律由数据库自增分配，**禁止在迁移中硬编码 id 区间**；定位菜单用 `WHERE perm='...'`，给角色授权用
   `INSERT INTO sys_role_menu (role_id, menu_id) SELECT <角色id>, id FROM sys_menu WHERE perm='...'`
   动态解析。`perm` 是菜单业务定位键（唯一索引 `uk_sys_menu_perm`）；目录/菜单行 `perm` 为 NULL 时不受该唯一约束。
5. 依赖外部数据状态的数据迁移（如按默认口令哈希标记账号）要显式写清语义并可在 dev/test 下关闭（见 V63 注释与 `application-dev/test.yml`）。

## 3. 表清单策略：迁移目录自取 + 主题域导读

**不在此逐张罗列表清单**——迁移持续新增，手抄清单必然过期。开发/运维查表时：`ls backend/src/main/resources/db/migration/ | sort` 取全部 V 文件，按文件名定位所需表；下表为主题域导读，帮助按域找到入口表（表名均来自迁移目录，列结构以对应 V 文件为准）：

**系统与权限**

- `sys_user`（含租户、审计、逻辑删除、密码策略字段）、`sys_role`、`sys_menu`、`sys_dept`、`sys_post`
- 关联表：`sys_user_role` / `sys_role_menu` / `sys_role_dept` / `sys_user_post`
- 资源权限：`sys_api_perm`（method+path → 所需权限编码，URL 层资源级校验）、`sys_data_permission`（表 → 行级表达式列映射，可热加载）

**基础数据与租户**

- `sys_dict_type` / `sys_dict_data`（`is_shared=1` 共享字典，tenant_id=0 全局一份）、`sys_config`（参数）、`sys_tenant`

**认证与安全**

- TOTP 密钥、`must_change_password`/`password_changed_at`（V63 密码策略）均在 `sys_user` 列上
- OAuth/SSO：`sys_oauth_config`（平台级配置，租户 1 可管）、`sys_oauth_client`（租户私有，`client_id` 全局唯一）、`sys_user_oauth`（绑定关系）
- MCP：`sys_mcp_server`

**日志与审计**

- `sys_login_log` / `sys_oper_log` / `sys_sql_log`、`sys_audit_log` / `sys_field_audit_log`（字段级审计）、`sys_job` / `sys_job_log`（Quartz JDBC 存储）、`sys_alert_rule`

**消息、通知与文件**

- `sys_notice` / `sys_notice_read`、`sys_message` / `sys_message_read` / `sys_message_template`、`sys_channel_config` / `sys_channel_log`（消息渠道）、`sys_outbox`（可靠投递发件箱）、`sys_file`（文件元数据）

**工作流**

- `sys_workflow` / `sys_workflow_log`、`sys_process_def` / `sys_process_node`
- Warm-Flow 引擎持久化表：`flow_definition` / `flow_node` / `flow_skip` / `flow_ins_order` / `flow_task` 等（V32 引入，`flow_*` 前缀）

**AI**

- `ai_service_config`、`ai_task` / `ai_task_result`、`ai_prompt_template` / `ai_knowledge_base` / `ai_knowledge_doc`

**设备与物联**

- `sys_device`、`device_thing_model`（物模型）、`device_telemetry`（遥测，纯追加时序）

**报表 / 表单 / 导入导出 / 大屏**

- `report_definition`（自定义 SQL 报表）、`form_definition` / `form_instance`、`import_export_template` / `import_export_job`、`screen_template`

## 4. 关键设计规约

### 4.1 审计列与填充

核心业务表普遍带下列列（以各表迁移为准，不是每表全部具备）：

| 列 | 语义 | 维护方 |
| --- | --- | --- |
| `tenant_id` | 租户标识（多租户表） | 租户拦截器自动追加/落库 |
| `created_by` / `updated_by` | 操作人（用户 id） | `MyMetaObjectHandler` 自动填充 |
| `created_at` / `updated_at` | 创建/更新时间 | `MyMetaObjectHandler` + DB 默认值 |
| `deleted` | 逻辑删除标记 | MyBatis-Plus `@TableLogic` |
| `version` | 乐观锁版本 | `OptimisticLockerInnerInterceptor` 校验，更新时 +1 |

- `application.yml`：`logic-delete-field: deleted`、`logic-delete-value: 1`、`logic-not-delete-value: 0`。
- 实体缺失某字段时自动填充会跳过（非严格填充），无需逐实体声明。

### 4.2 主键口径

- 主键统一 `BIGINT UNSIGNED NOT NULL AUTO_INCREMENT`（存量表于 `V28__primary_keys_unsigned.sql` 统一；新表按同口径建）。
- 关联表主键为复合键（如 `sys_user_role(user_id, role_id)`）。

### 4.3 逻辑删除 + 唯一键取舍（禁止该组合）

- **禁止新增「逻辑删除 + 业务编码唯一键」组合**：逻辑删除后行仍在，`(tenant_id, 编码)` 唯一键仍占位——同名/同编码数据永远无法重建（exists 检查过滤 `deleted=0` 判定"不存在"，INSERT 却命中唯一键）。
- **存量表处置**（`sys_user`/`sys_role`/`sys_post`/`sys_dict_type`/`sys_config`/`ai_prompt_template` 等）：删除路径统一在删除前调用 `cn.admin.scaffold.common.UniqueKeyRelease.releaseCode`，把业务编码改写为 `原编码#del#时间戳`（如 `admin#del#20240101120000000`）释放唯一键、保留逻辑删除行（审计可追溯），同名数据可立即重建。
- **确需例外时**：唯一键必须包含 `deleted` 且 `deleted` 存删除时间戳（约定详见 [`../architecture-conventions.md`](../architecture-conventions.md)）。仓库已发布迁移中的 `deleted` 均为 `TINYINT NOT NULL DEFAULT 0`（0=在存，1=已删），尚无时间戳实现——该条款适用于新增表设计，若未来落地时间戳删除列，以对应新迁移为准。
- 用户名唯一性按租户隔离：`sys_user` 用 `uk_sys_user_tenant_username(tenant_id, username)`（V33 替换 V1 的全局 `uk_sys_user_username`），多租户下各租户可存在同名账号。

### 4.4 无外键的设计决策

迁移目录中**不使用 FOREIGN KEY / REFERENCES 约束**（全目录核对无外键声明）。关联完整性由应用层维护（Service 显式校验、删除前检查引用、MyBatis-Plus 逻辑删除），以规避多租户/跨库与迁移顺序复杂度、减少锁竞争。这意味着：层叠行为需应用层实现；孤儿数据防护靠业务校验与数据权限，不在 DB 层兜底。

### 4.5 索引规约

- 命名：普通/查询组合索引 `idx_<表>_<语义>`（如 `idx_sys_message_tenant_receiver`、`idx_ai_task_tenant_status`）；唯一索引 `uk_<表>_<语义>`（如 `uk_sys_user_tenant_username`）。
- 高频查询先建组合索引且租户/状态类条件列放前（参考 `V27__add_common_query_indexes.sql`：`(tenant_id, username/status/...)` 模式），遥测等时序表按 `(device_id, property_key, occurred_at)` 建模。
- 迁移中建索引用 `CREATE INDEX` 或表内 `KEY`；删除/替换唯一键用独立 `ALTER TABLE`（见 V33）。

### 4.6 多租户隔离机制

- MyBatis-Plus `TenantLineInnerInterceptor` 按 `TenantContext` 当前租户自动追加 `tenant_id` 条件（`MybatisPlusConfig`）；自定义 `<script>` Mapper 方法与跨租户查询按需显式处理（租户白名单配置见 `MybatisPlusConfig`，注释中说明了 OAuth 等平台级表为何不进白名单）。
- 数据权限：角色支持全部数据/本部门/本部门及以下/自定义部门四种范围，`DataScopeAspect` + `DataScopeInnerInterceptor` 从 `sys_data_permission` 配置读取受控表与关联列，可热加载；配置类/公告/消息等明确不施加行级过滤的语义以代码为准。

### 4.7 说明

- 升级/发布前确认迁移脚本与镜像版本一致：新增发布只追加 V 文件，不回头改旧脚本（§2）。
- 字段级审计、乐观锁冲突、逻辑删除组合等运行时行为以 `backend` 代码（`MyMetaObjectHandler`、`OptimisticLockerInnerInterceptor`、`UniqueKeyRelease` 等）与 `application.yml` 为准，本文件仅作导读。
