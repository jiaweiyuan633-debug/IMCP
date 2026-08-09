# 架构说明

## 总体结构

Y15 双端管理平台由四个运行时组成：

- `backend`：Spring Boot 3.3，承载认证、系统管理、监控、AI 编排、工作流、租户与文件能力
- `ai-service`：FastAPI 异步任务服务，负责算法执行与回调
- `frontend`：后台管理系统，Vue3 + Ant Design Vue
- `website`：Y15智能管理平台官网，Vue3

## 数据流

1. 管理端通过 `/api/**` 访问 Java 后端。
2. Java 后端通过 JWT 认证，数据权限由 `@DataScope` + MyBatis SQL 拦截器统一注入。
3. AI 任务由 Java 创建后调用 Python 服务，Python 执行完成后通过回调写回结果。
4. 通知和 AI 任务状态通过 SSE 推送；通知支持 Redis 广播以支持多实例。
5. 监控指标由 Prometheus 采集，链路追踪由 Brave 上报 Tempo。

## 关键机制

- 多租户：`TenantContext` + MyBatis-Plus 租户拦截器
- 数据权限：`@DataScope` AOP + `DataScopeInnerInterceptor`
- 审计：操作日志 AOP + `sys_audit_log`
- 文件访问：HMAC 签名 Token，短期有效
- 2FA：TOTP 密钥 AES-GCM 加密存储
- 工作流：流程定义、条件/并行节点、表单数据、超时提醒
- 外部能力：AI 任务、告警 Webhook 通过 Manager 封装，文件存储通过 `FileStorage` 接口隔离
- 日志安全：操作日志经 `LogMaskUtils` 递归脱敏密码、Token、API Key、手机号、邮箱等字段
- 统一错误码：`ResultCode` 覆盖认证、参数、AI 回调与工作流状态，前端语言包同步维护

## 当前完成度

- API 版本化 `/api/v1/**` 已提供
- PWA 离线缓存、Playwright E2E、覆盖率门槛已接入
- GitOps 通过 ArgoCD 交付 `k8s/helm/admin-scaffold`
- 阿里巴巴 Java 开发手册七大模块已完成分批整改，当前状态见 `docs/alibaba-compliance.md`
- 分层、命名、错误码、数据访问与测试规约见 `docs/architecture-conventions.md`

## 后续方向

- 消息中间件替换 SSE 的跨实例广播
- 对象存储接入 CDN 与预签名 URL
- 工作流继续扩展会签、加签与动态表单
