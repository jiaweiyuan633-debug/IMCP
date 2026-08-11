# 架构说明

## 总体结构

智能管理平台由四个运行时组成：

- `backend`：Spring Boot 3.3，承载认证、系统管理、监控、AI 编排、工作流、租户与文件能力
- `ai-service`：FastAPI 异步任务服务，负责算法执行与回调
- `frontend`：后台管理系统，Vue3 + Ant Design Vue
- `website`：智能管理平台官网，Vue3

## 数据流

1. 管理端通过 `/api/**` 访问 Java 后端。
2. Java 后端通过 JWT 认证，数据权限由 `@DataScope` + MyBatis SQL 拦截器统一注入。
3. AI 任务由 Java 创建后调用 Python 服务，Python 执行完成后通过回调写回结果。
4. 通知和 AI 任务状态通过 SSE 推送；通知支持 Redis 广播以支持多实例。
5. 监控指标由 Prometheus 采集，链路追踪由 Brave 上报 Tempo。

## 关键机制

- 多租户：`TenantContext` + MyBatis-Plus 租户拦截器
- 数据权限：`@DataScope` AOP + `DataScopeInnerInterceptor`，行级映射表 `sys_data_permission` 可配置热加载（`DataPermissionRuleResolver`）
- RBAC 资源级权限：按钮级 `@PreAuthorize` + URL 层 `ApiPermAuthorizationFilter`，`sys_api_perm`（method+path → 权限编码）内存注册表实时热加载
- 审计：操作日志 AOP + `sys_audit_log`
- 文件访问：HMAC 签名 Token + HTTP Range 206/416 + 预签名直传 URL（MinIO）
- 文件上传：分片上传（Redis 任务元数据 + 分布式锁合并 + sha256 校验）、秒传（按租户+sha256）
- 2FA：TOTP 密钥 AES-GCM 加密存储
- 工作流：流程定义、条件/并行节点、表单数据、超时提醒；`WorkflowTimeoutScanner` 定时扫描超时节点并通知
- 消息：站内消息 TEXT/HTML 富文本（`content_type`）、`sys_message_template` 模板渲染发送、渠道发送 `@Retryable` 重试（`ChannelSendException` 触发）
- 字典：共享字典（tenant_id=0）+ 租户覆盖模型 + 租户粒度缓存失效（共享数据变更清全租户）
- 报表定义化：`report_definition` 存 SQL 定义，执行前强制只读校验（仅 SELECT、禁 DDL/DML/注释攻击），`/execute` 返回数据行
- 设备物模型/遥测：`device_thing_model`（属性/事件/服务三要素 JSON）+ `device_telemetry` 时序遥测，上报按物模型校验属性类型
- 导入导出中心：`import_export_template` 列映射与校验规则 + `import_export_job` 异步任务（`ScheduledTaskLock` 轮询调度），`ImportExportHandler` SPI 扩展实体（内置字典数据处理器），导入失败生成明细文件可下载
- 低代码表单引擎：`form_definition` 零依赖 schema 校验 + `form_instance` 提交数据校验与审批流转（SUBMITTED → APPROVED/REJECTED），草稿不渲染不接收提交
- 外部能力：AI 任务、告警 Webhook 通过 Manager 封装，文件存储通过 `FileStorage` 接口隔离
- 日志安全：操作日志经 `LogMaskUtils` 递归脱敏密码、Token、API Key、手机号、邮箱等字段
- 统一错误码：`ResultCode` 覆盖认证、参数、AI 回调、工作流状态与批次4 业务模块（报表/物模型/模板/表单），前端语言包同步维护

## 当前完成度

- API 版本化 `/api/v1/**` 已提供
- PWA 离线缓存、Playwright E2E、覆盖率门槛已接入
- GitOps 通过 ArgoCD 交付 `k8s/helm/admin-scaffold`
- 阿里巴巴 Java 开发手册七大模块已完成分批整改，当前状态见 `docs/alibaba-compliance.md`
- 分层、命名、错误码、数据访问与测试规约见 `docs/architecture-conventions.md`

## 后续方向

- 消息中间件替换 SSE 的跨实例广播
- 对象存储接入 CDN（预签名直传已实现）
- 工作流继续扩展会签、加签；表单引擎与工作流表单回显打通
- URL 层资源权限在全部写接口上沉淀 `sys_api_perm` 规则（当前以用户/角色/菜单/字典为示例）
