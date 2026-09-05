# AI 服务（ai-service）使用与开发指南

本文面向两类读者：**接入方开发者**（在 `backend` 中新增/调整 AI 任务）与
**ai-service 维护者**（新增能力、扩展任务类型、排查任务失败）。所有涉及端点、
配置、目录的事实均可在仓库源码中核对，本文不重复罗列版本号类信息。

## 定位与职责

`ai-service` 是独立的 Python（FastAPI）异步 AI 任务执行服务，与 Spring Boot
后端（`backend`）通过 HTTP 协作，二者不共享进程与存储（仅共享 Redis 作为任务
队列 / 向量存储 / 状态暂存）。

职责边界：

- 接收后端派发的异步任务（`biz_type` 指定能力），按优先级/超时/重试策略执行；
- 提供同步调用的 AI 接口（对话、流式对话、Embedding、向量检索、定时管道管理）；
- 内置能力：多供应商 LLM（对话/流式/工具调用/Embedding）、RAG（分块、文档解析、
  向量入库与检索）、轻量 ML（TF-IDF 分类 / KMeans 聚类）、可插拔 OCR、智能体工具
  调用、定时管道、PII 检测与脱敏；
- 任务完成后以 HMAC 签名回调写回后端，失败按原因分类并支持死信查询。

`biz_type` 是任务能力标识，注册表见 `app/services/__init__.py::build_services`，
当前注册：

| biz_type | 能力 | 实现 |
| --- | --- | --- |
| `text_summary` | 文本摘要 | `app/services/text_summary.py` |
| `keyword_extract` | 关键词提取 | `app/services/keyword_extract.py` |
| `llm_chat` | LLM 对话（同步直连另有 `/chat`） | `app/services/llm_chat.py` |
| `embedding` | 文本向量化 | `app/services/embedding.py` |
| `rag_ingest` | 知识分块 + 向量入库 | `app/services/rag_service.py` |
| `rag_retrieve` | 向量检索 | `app/services/rag_service.py` |
| `doc_parse` | 多格式文档解析（pdf/docx/xlsx 走可选依赖） | `app/services/doc_parse.py` |
| `ml_classify` | 文本分类（训练/预测） | `app/services/ml_service.py` |
| `ml_cluster` | 文本聚类 | `app/services/ml_service.py` |
| `ocr` | 图片文字识别（mock / tesseract） | `app/services/ocr_service.py` |
| `agent` | 智能体工具调用 | `app/services/agent_service.py` |
| `pii_mask` | PII 检测脱敏 | `app/services/pii_service.py` |
| `schedule_register` | 注册定时管道 | `app/services/schedule_service.py` |

> 每个 `biz_type` 的入参/出参结构由对应 `run(params)` 实现定义；同步 HTTP 面
> 的请求/响应模型见 `app/schemas/ai.py`。以“后端创建任务 → ai-service 执行 → 回调”
> 接新的能力时，字段契约以这两处源码与后端 `AiTaskCreateRequest` 为准。

## 架构与调用链

```
backend (Spring Boot)                          ai-service (FastAPI)
  AiTaskService/AiPythonClient                    TaskManager
  ├─ POST {AI_BASE_URL}/api/v1/tasks  ───────────▶ 建单（task_no 去重 → 409）
  │     Authorization: Bearer <apiKey>             Redis zset 优先级队列
  │     callback_url = {CALLBACK_BASE_URL}         有界工作协程 + 超时（wait_for）
  │              + /api/ai/callback/task            ServiceRegistry: biz_type → 执行器
  │                                                失败分类：timeout / non_retryable /
  │                                                retries_exhausted → 延迟重试 / 死信
  ◀─── POST callback_url（HMAC-SHA256 签名）───────── 成功或终态失败时回调
        X-Ai-Timestamp / X-Ai-Signature
  AiTaskService.handleCallback：验签 + 幂等 + 落库
```

关键事实（以源码为准）：

- **派发**：`AiPythonClient.createTask` 向 `config.baseUrl + "/api/v1/tasks"` POST
  `TaskCreateRequest`，携带 `Authorization: Bearer <apiKey>`。`baseUrl`/`apiKey`
  来自后端 AI 服务配置（DB 实体 `AiServiceConfigDO`，`apiKey` 密文落库、
  提交/回调验签前由 `SecretCipher` 解密），生产由 `app.ai-base-url`
  （`AI_BASE_URL`）与 Helm values 提供。
- **回调目标**：后端建单时把 `callbackUrl` 置为 `app.callback-base-url`
  （`CALLBACK_BASE_URL`）+ `/api/ai/callback/task`。
- **回调签名**：ai-service 以 `AUTH_TOKEN` 为密钥对
  `timestamp + "\n" + 紧凑 JSON 请求体` 做 HMAC-SHA256，经
  `X-Ai-Timestamp` / `X-Ai-Signature` 头携带；请求体为最小化 JSON（`separators=(",",":")`），
  回调自身失败会退避重试数次。后端 `AiTaskService.validCallbackHmac` 做字节级一致
  校验，与 ai-service `app/tasks/manager.py::_callback` 保持同一消息格式。
- **回调地址安全**：ai-service 建单与执行回调前都会校验 `callback_url`（SSRF 防护，
  见 `app/core/callback_security.py`）：未配置白名单时仅允许回环地址；配置了
  `CALLBACK_ALLOWED_ORIGINS` 后按 origin 精确白名单放行，危险地址段（云元数据、
  链路本地等）一律拒绝。

## 部署方式

- **本地开发**：在 `ai-service/` 下从 `ai-service/.env.example` 复制出 `.env`
  （`Settings` 读取 `env_file=".env"`），然后运行
  `ai-service\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000`；
  或直接执行仓库根 `scripts/start-dev.ps1`（同时拉起 backend / frontend 等）。
- **容器**：`ai-service/Dockerfile` 多阶段构建（`uv` 同步依赖、`python:3.11-slim`
  运行镜像、内置 `tesseract-ocr`），监听 8000 端口，命令为
  `uvicorn app.main:app --host 0.0.0.0 --port 8000`。本地全栈用
  `docker compose up -d --build`（service 名为 `ai-service`）。
- **生产**：以 Helm Chart `k8s/helm/admin-scaffold` 为准（镜像 `ai-service`），
  密钥由 Secret 注入；K8s 探针语义见下方监控端点。

启动要点：`AUTH_TOKEN` 在代码中**无默认值**，未注入即启动失败（fail-fast）；
本地联调用 `.env.example` 的模板值，生产必须由 Secret 注入并与后端 AI 服务配置
`apiKey` 保持一致。

## 配置项

配置字段定义在 `app/core/config.py` 的 `Settings`（pydantic-settings）；环境变量名
为字段名的大写形式，`.env.example` 是本地开发模板，**未列在模板中但带默认值的键**
仅在你需要覆盖默认值时注入。下面先列 `.env.example` 中确实存在的键：

| 环境变量 | 默认 | 说明 |
| --- | --- | --- |
| `APP_NAME` | `ai-service` | 应用名（探针/日志使用） |
| `REDIS_URL` | `redis://localhost:6379/0` | 队列、向量存储、任务状态用的 Redis |
| `AUTH_TOKEN` | 无默认值，必填 | 入站 Bearer 鉴权与出站回调 HMAC 共用，须与后端 AI 配置 `apiKey` 一致 |
| `DEFAULT_TIMEOUT_SECONDS` | `60` | 未显式指定时任务的执行超时（秒） |
| `MAX_TIMEOUT_SECONDS` | `3600` | 单任务超时上限，建单时对请求 timeout 做 clamp |
| `CALLBACK_ALLOWED_ORIGINS` | `[]` | 回调 SSRF 白名单（JSON 数组）；生产必配，如 `["http://admin-backend:8080"]` |
| `LLM_PROVIDERS` | `{}` | OpenAI 兼容提供方字典（JSON 字符串），留空用 `mock` |
| `LLM_DEFAULT_PROVIDER` | `mock` | 默认提供方 |
| `WORKER_COUNT` | `2` | 每副本任务消费工作协程数 |
| `OCR_PROVIDER` | `mock` | `mock` / `tesseract` |
| `OCR_FAIL_FAST` | `false` | `true` 时 OCR 提供方探测/构造失败直接抛错，不静默回退 `mock` |

`Settings` 中还定义了下述键（默认值见 `config.py`，未进 `.env.example`）：
`CALLBACK_CLOCK_SKEW_SECONDS`（回调时间戳允许偏差，默认 300）、`CORS_ORIGINS`、
`LLM_TIMEOUT_SECONDS`、`LLM_RETRY_MAX_ATTEMPTS`、`LLM_RETRY_BASE_SECONDS`、
`VECTOR_NAMESPACE_PREFIX`、`VECTOR_DEFAULT_DIM`、`RETRY_BACKOFF_SECONDS`、
`TASK_MAX_RETRY`、`QUEUE_*`（队列键名/死信裁剪长度 `QUEUE_DEAD_MAX_LEN`）、
`TASK_TTL_SECONDS`、`STALE_TASK_LEASE_GRACE_SECONDS`（崩溃自愈租约宽限）、
`PII_MASK_CHAR`、`SCHEDULER_*`（定时管道键名/轮询/保留天数）。

> 维护约定：新增配置时先加 `Settings` 字段并同步 `.env.example` 与本文；不要把
> 生产 Secret 写进 `.env.example`。

## HTTP 接口

接口分三层：**业务路由**（`/api/v1/**`，见 `app/api/routes.py`）、**基础设施端点**
（定义在 `app/main.py`）。业务路由中除 `/api/v1/ping` 外全部要求
`Authorization: Bearer <AUTH_TOKEN>`（FastAPI `HTTPBearer`，按 `hmac.compare_digest`
恒时比较；失败返回 401）。基础设施端点（探针、指标）不参与业务鉴权。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/ping` | 无 | 连通性探测，返回 `{"message":"pong",...}` |
| POST | `/api/v1/tasks` | Bearer | 创建任务，返回 202 + `TaskStatusResponse`；`task_no` 重复返回 409 |
| GET | `/api/v1/tasks/dead` | Bearer | 死信列表（新失败在前；`limit` 默认 100、上限 1000） |
| DELETE | `/api/v1/tasks/dead` | Bearer | 清空死信，返回 `{"purged": N}` |
| GET | `/api/v1/tasks/{task_id}` | Bearer | 查询任务状态与结果；不存在返回 404 |
| POST | `/api/v1/tasks/{task_id}/retry` | Bearer | 手动重试失败任务 |
| POST | `/api/v1/chat` | Bearer | 非流式对话（多供应商；`mask_pii` 时做进出站 PII 脱敏） |
| POST | `/api/v1/chat/stream` | Bearer | SSE 流式对话：`data: {"delta": ...}`，结束帧 `data: [DONE]` |
| POST | `/api/v1/embeddings` | Bearer | 文本向量化，返回 `vectors/dim/model/provider` |
| POST | `/api/v1/vectors/upsert` | Bearer | 文本 → 向量 → 写入命名空间 |
| POST | `/api/v1/vectors/search` | Bearer | 向量/文本检索（`top_k`、`threshold`） |
| POST | `/api/v1/schedules` | Bearer | 注册定时管道；触发表达式非法返回 422 |
| GET | `/api/v1/schedules` | Bearer | 列出定时管道 |
| DELETE | `/api/v1/schedules/{schedule_id}` | Bearer | 删除定时管道 |
| GET | `/health` | 无 | 探活（依赖 Redis，不可用返回 503；兼容旧客户端） |
| GET | `/livez` | 无 | 纯进程存活探针（恒 200，供 K8s liveness） |
| GET | `/readyz` | 无 | 就绪探针（依赖 Redis，供 K8s readiness） |
| GET | `/metrics` | 无 | Prometheus 指标（**根路径**，见下节） |

错误语义（见 `routes.py` 与 `app/tasks/manager.py`）：

- 同步端点传入未知 `provider`、`/vectors/search` 未提供 `text` 或 `vector`：400
  （客户端错误，而非 500）；
- 同名 `task_no` 去重命中：409；
- 任务不存在：404；未认证/凭证不符：401；
- 定时表达式非法：422；
- 任务执行失败不会在 HTTP 层报错——任务进入终态后通过回调透传
  `status/error/reason`，`reason` 取值 `timeout` / `non_retryable` /
  `retries_exhausted`（成功为 `null`），由调用方据此区分“值得重试”与“重试无意义”。

实现注意（新增路由时易踩）：字面量路由 `/api/v1/tasks/dead` 必须先于参数路由
`/api/v1/tasks/{task_id}` 注册（Starlette 按注册顺序匹配），否则 `/tasks/dead`
会被参数路由吞成“查询名为 dead 的任务”。

## 内部机制与设计要点

- **多供应商 LLM**：`LLM_PROVIDERS` 以 JSON 注入 OpenAI 兼容提供方（OpenAI /
  DeepSeek / 通义 / Ollama / vLLM 等共用协议，见 `app/llm/openai_compat.py`）；
  未配置时用 `mock` 提供方（确定性输出，测试可重复）。失败按 `LLMError`
  （网络/上游 5xx，可重试）与 `LLMConfigError`（认证/模型不存在，不可重试）分流，
  非任务路径（`/chat`、Agent 引擎）由 `app/llm/retry.py` 指数退避。
- **任务队列**：Redis zset 优先级队列（`-priority` 排序）承载就绪任务、延迟队列
  做重试退避，有界工作协程 + `asyncio.wait_for` 超时保护；`NonRetryableError`
  不重试，超限失败进死信队列（`failed_at/biz_type/retry_count` 富化，供运维排障）。
  崩溃自愈：启动时回收租约过期的 RUNNING 遗留任务重新入队
  （`TaskManager.recover_stale_tasks`）。
- **向量检索**：Embedding 产出写入 Redis（前缀 `VECTOR_NAMESPACE_PREFIX`，默认
  `ai:vec`），检索用精确余弦（纯 Python 实现）；规模增大后应切换 Milvus/pgvector
  （后端知识库已可选 Milvus 后端，见 `application.yml` 的 `app.milvus`）。
- **RAG**：智能分块（段落/句子边界 + overlap）、多格式解析（txt/md/csv/json
  原生；pdf/docx/xlsx 为可选依赖，由 Dockerfile 以 `uv sync --extra parsing` 安装）。
- **轻量 ML**：纯 Python TF-IDF + KNN 分类 + KMeans 聚类，零重依赖，镜像精简。
- **PII**：规则检测（身份证/手机/邮箱/银行卡/IP 等）+ 递归结构脱敏；对外对话
  默认可启用进出站脱敏（`mask_pii`），流式场景经 `StreamMasker` 跨分片处理。
- **OCR**：`mock` / `tesseract` 可插拔；`tesseract` 不可用时默认回退 `mock` 并告警，
  `OCR_FAIL_FAST=true` 时改为直接抛错（避免“无声数据造假”）。
- **指标**：`/metrics` 挂在根路径（`main.py` 直接提供，而非 `routes.py`），抓取时
  实时采样三条队列深度（`ready/delayed/dead`，Redis 不可用时置 `-1`）；业务指标
  前缀 `ai_*`（`ai_task_created_total`、`ai_task_failed_total{reason}`、
  `ai_queue_depth{queue}`、`ai_worker_count`），详见 `app/core/metrics.py`。

## 测试

```bash
# 在 ai-service/ 目录下
./.venv/Scripts/python.exe -m pytest -q     # Windows
uv run pytest -q                            # 有 uv 时
```

测试全部使用 `fakeredis` 与确定性 `mock` 提供方，无外部依赖可离线运行。覆盖率
门禁由 `pyproject.toml` 的 pytest `addopts` 生效（`--cov-fail-under=…`，以文件为准），
CI（`.github/workflows/ci.yml`）执行 `uv run pytest -q --locked` 与 ruff 检查。

## 扩展指引：新增一个 biz_type 服务

以“新增 `translate`（文本翻译）任务类型”为例，步骤：

1. **实现执行器**：在 `app/services/` 新增 `translate_service.py`，继承
   `app/services/base.py::BaseTaskService`，实现
   `async def run(self, params: dict) -> dict`（抛异常由任务管理器按分类处理）。
2. **注册**：在 `app/services/__init__.py::build_services` 的返回字典中加入
   `"translate": TranslateService(context)`。若执行器只依赖外部 LLM/向量等，可通过
   `ServiceContext` 取到已装配的 `providers/vectors/redis`。
3. **错误分类**：瞬时/上游问题抛 `RetryableError`（`app/tasks/errors.py`）走重试；
   参数、配置、认证等确定性问题抛 `NonRetryableError`，直接终态失败进死信；执行
   超时由任务管理器按 `timeout` 处理，无需自行捕获。
4. **同步 HTTP 面（可选）**：如需后端直连调用（不走任务队列），在 `app/schemas/ai.py`
   增加请求/响应模型，在 `app/api/routes.py` 加端点并挂 `Depends(require_api_token)`。
5. **测试**：仿照 `tests/` 现有用例，用 `fakeredis` + `mock` 提供方覆盖成功、超时、
   不可重试与回调签名路径。
6. **回调侧**：后端如需感知新类型，确认 `AiTaskService` 建单/回调处理的 `biz_type`
   白名单或落库字段是否需要扩展（回调节点 `/api/ai/callback/task` 通用）。

相关文档：[API 接口约定](./api/README.md) ｜ [运维手册（监控端点/告警）](./runbook.md)。
