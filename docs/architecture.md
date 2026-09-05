# 架构说明

## 总体结构

智能管理平台由四个运行时组成：

- `backend`：Java 业务后端（Spring Boot，版本以 `backend/pom.xml` 为准），承载认证、系统管理、监控、AI 编排、工作流、租户与文件能力
- `ai-service`：Python（FastAPI）异步任务服务，负责算法执行与回调
- `frontend`：后台管理系统（Vue3 + Ant Design Vue）
- `website`：对外官网（Vue3）

## 数据流

1. 管理端通过 `/api/**` 访问 Java 后端（对外同时提供 `/api/v1/**` 版本化前缀，由 `ApiVersionFilter` 归一化到控制器映射）。
2. Java 后端通过 JWT 认证；数据权限由 `@DataScope` + MyBatis SQL 拦截器统一注入。
3. AI 任务由 Java 后端创建后调用 Python 服务，Python 执行完成后以 HMAC 签名回调写回结果。
4. 通知与 AI 任务状态通过 SSE 推送；通知支持 Redis 广播以支持多实例。
5. 监控指标由 Prometheus 采集；链路追踪通过 Micrometer Tracing（Brave）上报到可配置的 Zipkin 兼容端点。

## 关键机制

- 认证与口令：JWT + Redis 令牌、刷新令牌（httpOnly Cookie 优先）、TOTP 双因素（密钥 AES-GCM 加密存储）；服务端强制口令策略（默认口令首登改密、口令过期拦截，见 `PasswordPolicyEnforcementFilter`），改密/重置/停用/调整角色会吊销对应用户的既有会话
- 多租户：`TenantContext` + MyBatis-Plus 租户拦截器
- 数据权限：`@DataScope` AOP + `DataScopeInnerInterceptor`，行级映射表 `sys_data_permission` 配置热加载（`DataPermissionRuleResolver`）
- RBAC 资源权限：按钮级 `@PreAuthorize` + URL 层 `ApiPermAuthorizationFilter`（`sys_api_perm` 的 method+path → 权限编码注册表热加载；匹配前对 URI 做与 MVC 同口径的规范化）
- 审计：操作日志 AOP + `sys_audit_log`、字段级审计 `sys_field_audit_log`
- 文件访问：HMAC 签名访问令牌 + HTTP Range（206/416）+ 预签名直传 URL（MinIO）
- 文件上传：分片上传（Redis 任务元数据 + 分布式锁合并 + SHA-256 校验）、秒传（按租户 + SHA-256）
- 工作流：流程定义、条件/并行节点、表单数据、超时提醒（`WorkflowTimeoutScanner`）
- 消息：站内消息 TEXT/HTML 富文本、模板渲染发送、渠道发送失败重试（`@Retryable`）、WebSocket + SSE 实时推送
- 字典：共享字典（tenant_id=0）+ 租户覆盖 + 租户粒度缓存失效
- 报表定义化：`report_definition` 存 SQL 定义，执行前只读校验（仅 SELECT、禁 DDL/DML/注释攻击）
- 设备物模型/遥测：`device_thing_model`（属性/事件/服务三要素）+ `device_telemetry` 时序遥测
- 导入导出中心：`import_export_template` 列映射与校验规则 + `import_export_job` 异步任务，`ImportExportHandler` SPI 扩展
- 表单引擎：`form_definition` 零依赖 schema 校验 + `form_instance` 提交校验与审批流转
- 外部能力收敛：AI 任务、告警 Webhook 走 Manager 封装；文件存储通过 `FileStorage` 接口隔离本地/MinIO
- 日志安全：操作日志经 `LogMaskUtils` 递归脱敏密码、Token、API Key、手机号、邮箱等字段；敏感凭据落库加密（`SecretCipher`，AES-256-GCM）
- 统一错误码：`ResultCode` 覆盖认证、参数、AI 回调、工作流状态与各业务模块，前端语言包同步维护

## 现状速览

- API 提供 `/api/v1/**` 版本化前缀（重写为 `/api/**` 控制器映射）
- PWA 离线缓存、Playwright E2E、覆盖率门槛均已接入 CI
- GitOps 通过 ArgoCD 交付 `k8s/helm/admin-scaffold`
- 阿里巴巴 Java 开发手册规约执行说明见 `docs/alibaba-compliance.md`
- 分层、命名、错误码、数据访问与测试规约见 `docs/architecture-conventions.md`

## 演进方向

- 消息中间件替换 SSE 的跨实例广播（预留）
- 对象存储接入 CDN（预签名直传已具备）
- 工作流继续扩展会签/加签，表单引擎与工作流表单回显打通
- URL 层资源权限规则随新模块逐步沉淀（新接口默认显式授权，配置见 `SecurityConfig`）
