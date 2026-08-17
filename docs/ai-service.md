# AI 服务（ai-service）

FastAPI 异步 AI 任务服务，Java 后端（`backend`）通过 `/api/v1/tasks` 派发任务，
Python 侧执行完成后以 HMAC 签名回调写回结果。批次3 在原有「文本摘要 / 关键词提取」
基础上深化为完整 AI 能力集：**真实 LLM（多供应商 + 流式）**、**真向量 Embedding 与检索**、
**RAG 深化（分块 / 文档解析 / 混合检索）**、**轻量 ML**、**可插拔 OCR**、
**智能体工具调用**、**定时管道** 与 **PII 检测脱敏**，任务队列升级为「优先级 + 超时 +
可重试分类 + 去重 + 死信」模型。

## 架构与数据流

```
backend (Spring Boot)                 ai-service (FastAPI)
  AiTaskService ── POST /api/v1/tasks ──────────▶ TaskManager
       │                                          │ Redis 队列（优先级/延迟/死信）
       │                                          ▼ 工作线程池（超时保护）
       │                                    ServiceRegistry（biz_type → 服务）
       │                                          │
       │                                        Mock/OpenAI 兼容 LLM（多供应商）
       │                                          │ 真实流式/工具/Embedding
  ◀── HMAC 回调 ────────────────────── 成功/失败  ──┘
```

## 能力与任务类型

| biz_type | 能力 | 入参要点 | 出参要点 |
| --- | --- | --- | --- |
| `llm_chat` | 真实 LLM 对话 | `messages`, `model`?, `provider`? | `content`, `model`, `provider` |
| `embedding` | 文本向量化 | `texts` | `vectors`, `dim` |
| `rag_ingest` | 知识入库（分块+向量化） | `tenant_id`, `base_id`, `docs[{doc_id,title,content}]` | `chunks`, `docs` |
| `rag_retrieve` | 向量检索 | `tenant_id`, `base_id`, `query`, `top_k`? | `hits[{doc_id,score,payload}]` |
| `doc_parse` | 多格式文档解析 | `filename`, `content_b64` | `pages`, `pages_count`, `chars` |
| `ml_classify` | 文本分类（训练/预测） | `action=train/predict`, `name`, `labels/docs` 或 `doc` | 标签 + 置信度 |
| `ml_cluster` | 文本聚类 | `docs`, `k` | 簇与惯性 |
| `ocr` | 图片文字识别（可插拔） | `image_b64` | `text`, `provider` |
| `agent` | 智能体工具调用 | `user_prompt`, `max_steps`? | `final`, `steps` |
| `pii_mask` | 敏感信息检测脱敏 | `text` 或 `data` | `masked`/`text`, `detected` |
| `schedule_register` | 注册定时管道 | `name`, `schedule`, `biz_type`, `params` | 调度信息 |
| `text_summary` | 文本摘要（原有） | `content` | `summary` |
| `keyword_extract` | 关键词提取（原有） | `content` | `keywords` |

## HTTP 接口

除 `/metrics` 外全部需要 `Authorization: Bearer <AUTH_TOKEN>`（与后端 `AiServiceConfig.apiKey` 一致）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/tasks` | 提交任务（`priority` 0-9 越大越先执行，`timeout` 秒） |
| GET | `/api/v1/tasks/dead` | 列出死信队列（新失败在前；`limit` 默认 100、上限 1000） |
| DELETE | `/api/v1/tasks/dead` | 清空死信队列并返回清理条数 |
| GET | `/api/v1/tasks/{task_no}` | 查询任务状态 |
| POST | `/api/v1/tasks/{task_no}/retry` | 手动重试 |
| POST | `/api/v1/chat` | 非流式对话（多供应商） |
| POST | `/api/v1/chat/stream` | SSE 流式对话（`data: {"delta": ...}`，`[DONE]` 结束） |
| POST | `/api/v1/embeddings` | 文本向量化 |
| POST | `/api/v1/vectors/upsert` | 文本 → 向量 → 写入命名空间 |
| POST | `/api/v1/vectors/search` | 向量/文本检索（`top_k`、`threshold`） |
| POST | `/api/v1/schedules` | 注册定时管道（`interval:N` 或 `cron:* * * * *`） |
| GET | `/api/v1/schedules` | 列出定时管道 |
| DELETE | `/api/v1/schedules/{id}` | 删除定时管道 |
| GET | `/health` | 探活（Redis 不可用返回 503，兼容旧客户端） |
| GET | `/livez` | 纯进程存活探针（恒 200，供 k8s liveness；批次3） |
| GET | `/readyz` | 就绪探针（依赖 Redis，供 k8s readiness；批次3） |
| GET | `/metrics` | Prometheus 指标（根路径，见 main.py 注释） |

## 设计要点

- **多供应商 LLM**：`LLM_PROVIDERS` 环境变量以 JSON 注入 OpenAI 兼容提供方
  （OpenAI / DeepSeek / 通义 / Ollama / vLLM 等共用协议）；默认 `mock` 提供方
  确定性输出，开箱即用且测试可重复。失败按可重试（`LLMError`）与不可重试（`LLMConfigError`）分流。
- **真向量检索**：`embedding` 产出的真实向量存入 Redis（`ai:vec:{namespace}`），
  检索用精确余弦（纯 Python 实现，scaffold 规模够用；大规模应切换 Milvus/pgvector）。
- **RAG 深化**：智能分块（段落/句子边界 + overlap）、多格式文档解析
  （txt/md/csv/json 原生，pdf/docx/xlsx 可选依赖惰性加载）、向量入库与检索。
- **轻量 ML**：纯 Python TF-IDF + KNN 分类 + KMeans 聚类，零重依赖，Docker 镜像保持精简。
- **任务队列深化**：Redis zset 优先级队列（`-priority` 排序）、延迟队列做重试退避、
  有界工作池 + `asyncio.wait_for` 超时保护、`NonRetryableError` 不重试、
  同名任务去重（409）、超限失败进死信队列。失败按原因分类
  （`timeout` / `non_retryable` / `retries_exhausted`）随回调载荷 `reason` 透传，
  后端落 `ai_task.error_type`，便于区分瞬时超时（值得重试）与确定性错误（重试无意义）。
  死信记录富化 `failed_at` / `biz_type` / `retry_count`，经 `GET /tasks/dead`
  查询（新失败在前）、`DELETE /tasks/dead` 清理，运维可按时间/类型排障；
  字面量 `/tasks/dead` 必须先于参数路由 `/tasks/{task_id}` 注册（Starlette 按
  注册顺序匹配），否则会被参数路由吞成查询任务 "dead"。
- **PII 脱敏**：身份证/手机/邮箱/银行卡/IP 等规则检测 + 递归结构脱敏（敏感键整值打码）。

## 配置

```bash
AUTH_TOKEN=dev-ai-service-token        # 必填：与后端 AiServiceConfig.apiKey 一致，缺失即启动失败
LLM_DEFAULT_PROVIDER=mock              # 默认提供方
LLM_PROVIDERS='{"deepseek":{"base_url":"https://api.deepseek.com","api_key":"sk-...","model":"deepseek-chat","embedding_model":"text-embedding-v3"}}'
WORKER_COUNT=2                         # 工作线程数
RETRY_BACKOFF_SECONDS=0.5              # 重试退避基数（指数退避：0.5*2^n 封顶 60s，批次3）
TASK_MAX_RETRY=3                       # 最大重试次数
OCR_PROVIDER=mock                      # mock / tesseract（tesseract 不可用时回退 mock 并告警）
OCR_FAIL_FAST=false                    # true 时 OCR 探测失败直接抛错（不静默降级，批次3）
PII_MASK_CHAR=*                        # 脱敏字符
MAX_TIMEOUT_SECONDS=3600               # 单任务超时上限（默认 1h，超限 clamp）
CALLBACK_ALLOWED_ORIGINS=[]            # 回调 SSRF 白名单（JSON 数组，生产必配）
```

## 测试

```bash
./.venv/Scripts/python.exe -m pytest -q
```

全部测试使用 `fakeredis` 与确定性 `Mock` 提供方，无外部依赖可离线运行。
