from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class LLMProviderConfig(BaseModel):
    """LLM_PROVIDERS 单条提供方结构校验：base_url 必填、超时取正数。

    此前 llm_providers 是 ``dict[str, dict]``，base_url 缺失时 OpenAICompatible
    提供方仍被构造（空 URL），直到首请求才以难以理解的错误暴露；校验后配置
    期即拒绝非法结构（fail-fast）。
    """

    base_url: str = Field(min_length=1)
    api_key: str = ""
    model: str | None = None
    embedding_model: str | None = None
    timeout_seconds: int | None = Field(default=None, ge=1, le=3600)


class Settings(BaseSettings):
    app_name: str = "ai-service"
    redis_url: str = "redis://localhost:6379/0"
    # 入站鉴权密钥：任务接口校验（Authorization: Bearer）。必须与后端
    # AiServiceConfig.apiKey 保持一致。无默认值：未注入 AUTH_TOKEN 时构造
    # Settings 即抛错、服务启动失败（fail-fast），杜绝静默携带公开 dev 密钥上线；
    # 本地开发由 .env（.env.example 模板）显式提供，生产由 k8s Secret 注入。
    auth_token: str
    # 出站回调签名密钥（可选）：当前后端仍用同一共享密钥（AiServiceConfig.apiKey）
    # 同时做入站 Bearer 与回调 HMAC 验签，为保持跨端契约，默认留空 → 回退用
    # auth_token 签名（见 TaskManager._callback）。后端拆分出独立验签密钥后，
    # 在此注入新密钥并保持与后端配置一致即可轮换。风险说明：入站鉴权与出站签名
    # 共用同一密钥时，任一侧泄漏都会危及另一侧——由回调时间戳防重放与 SSRF
    # 白名单（callback_allowed_origins）缓解；生产密钥由 k8s Secret 管理。
    callback_hmac_key: str = ""
    # 回调 HMAC 时间戳允许偏差（秒），防重放
    callback_clock_skew_seconds: int = 300
    # 出站任务回调允许的 origin 白名单（JSON 数组，如 ["http://admin-backend:8080"]）：
    # 配置后回调地址必须与其中一项 origin 精确匹配；留空则仅允许 localhost/127.0.0.1
    # 回环地址（本地开发）。无论何种模式，云元数据（169.254.169.254）与链路本地/保留
    # 地址一律拒绝（SSRF 防护，见 app/core/callback_security.py）。生产必须显式注入。
    callback_allowed_origins: list[str] = []
    default_timeout_seconds: int = 60
    # 单任务超时上限（秒）：客户端可提交任意大的 timeout 长时间占用有限的工作协程，
    # 建单时对 request.timeout 做上限裁剪（见 TaskManager.create_task），保证任何
    # 任务最长执行可控，租约（= 超时 + 宽限）不会因超时过大而形同虚设
    max_timeout_seconds: int = 3600
    # 内部服务默认不开放跨域；如需浏览器直连，显式配置允许来源
    cors_origins: list[str] = []

    # ---------- 大模型（真实 LLM + 多供应商） ----------
    # 默认提供方：mock（确定性降级实现，开箱即用、测试可重复）；
    # 生产通过 LLM_PROVIDERS 配置一个或多个 OpenAI 兼容提供方（OpenAI/DeepSeek/通义/Ollama/vLLM 等）
    llm_default_provider: str = "mock"
    # 提供方字典：{"deepseek": {"base_url": "https://api.deepseek.com",
    #   "api_key": "sk-xxx", "model": "deepseek-chat", "embedding_model": "..."}}
    # 环境变量以 JSON 字符串注入（LLM_PROVIDERS），pydantic-settings 自动解析；
    # 单条结构经 LLMProviderConfig 校验（base_url 必填等），配置非法即启动失败
    llm_providers: dict[str, LLMProviderConfig] = {}
    llm_timeout_seconds: int = 120
    # LLM 重试（仅非任务路径：/chat 与 Agent 引擎）。任务路径由 TaskManager 负责重试，
    # 这里只对可重试异常（LLMError）做指数退避，配置/认证异常立即失败
    llm_retry_max_attempts: int = 3
    llm_retry_base_seconds: float = 0.5
    # 生产防呆（仿 OCR_FAIL_FAST）：置 true 时若默认提供方解析为 mock（未配置任何
    # 真实 LLM_PROVIDERS），服务启动直接抛错——防止生产「以为接了真实大模型、
    # 实际静默跑 mock 假数据」。本地开发保持 false。
    llm_fail_fast: bool = False

    # ---------- 向量存储（Redis 精确余弦；scaffold 规模够用，大规模换 Milvus/pgvector） ----------
    vector_namespace_prefix: str = "ai:vec"
    vector_default_dim: int = 384
    # 精确余弦检索的命名空间大小上限：超过上限拒绝检索并提示换专用向量库，
    # 避免一次性 hgetall 全库撑爆内存/线程（修复前对超大命名空间无保护）
    vector_search_max_vectors: int = 50_000

    # ---------- 任务队列（优先级 + 延迟重试 + 死信） ----------
    worker_count: int = 2
    retry_backoff_seconds: float = 0.5
    task_max_retry: int = 3
    queue_ready_key: str = "ai:queue:ready"
    queue_delayed_key: str = "ai:queue:delayed"
    queue_dead_key: str = "ai:queue:dead"
    # 死信队列长度上限：死信为纯写入、无消费者的 Redis list，若不裁剪则随失败
    # 总数无界增长、Redis 内存只升不降。失败落库时对队列做 rpush + ltrim 原子
    # 裁剪，仅保留最近 queue_dead_max_len 条（0 表示不裁剪，保留历史行为）。
    queue_dead_max_len: int = 1000
    task_ttl_seconds: int = 60 * 60 * 24
    # 崩溃自愈租约宽限（秒）：执行受 wait_for 超时约束，租约 = 任务超时 + 该宽限。
    # 存活执行必在租约到期前离开 RUNNING，故启动扫描可安全回收过期租约任务（见
    # TaskManager.recover_stale_tasks），多实例下也不会误回收其它实例正在执行的任务
    stale_task_lease_grace_seconds: float = 60.0

    # ---------- OCR（可插拔：mock / tesseract） ----------
    ocr_provider: str = "mock"
    # OCR_PROVIDER=tesseract 但探测/构造失败时，默认回退 Mock 并告警；
    # 置 true 则直接抛错（生产期望真实 OCR、杜绝"无声数据造假"时开启）
    ocr_fail_fast: bool = False

    # ---------- Redis 客户端与 CPU 工作线程 ----------
    # 零超时防护：默认不加 socket 超时时，Redis 断连/卡死会让 worker 循环、调度
    # 循环与 /health ping 永久阻塞（即便进程其它部分健康也无法自愈）。连接与
    # 命令超时 + 空闲连接周期 health check 兜底；/health 探活另加 asyncio.wait_for
    redis_socket_connect_timeout: float = 5.0
    redis_socket_timeout: float = 5.0
    redis_health_check_interval: float = 30.0
    # CPU 密集不可中断工作（OCR/PDF/KMeans/TF-IDF 训练）的有界线程池并发上限：
    # asyncio.wait_for 无法取消线程内工作，只能以有界池 + 输入预检控制占用
    cpu_thread_pool_size: int = 4
    # 全局请求体大小上限（字节）：超出返回 413。AI 服务内部接口，含任务建单
    # （params 里可能有 base64 文档），默认 8MB、可按部署调整；配合各 schema
    # 字段上限做纵深防御，防超大 payload 撑爆内存与下游
    max_request_body_bytes: int = 8 * 1024 * 1024

    # ---------- PII 脱敏 ----------
    pii_mask_char: str = "*"
    # 服务端强制出域脱敏：置 true 时，即使客户端请求 mask_pii=false 也会对发给
    # 外部 provider 的文本脱敏（mock 提供方在进程内完成、可豁免）。生产对接真实
    # LLM/Embedding 时必须置 true——此前 mask_pii 是请求级参数、客户端可传 false
    # 把原始手机号/身份证号直接送出服务边界（PIPL/等保出域控制失效）。
    pii_mask_required: bool = False

    # ---------- 定时管道 ----------
    scheduler_spec_key: str = "ai:sched:spec"
    scheduler_due_key: str = "ai:sched:due"
    scheduler_poll_seconds: float = 1.0
    scheduler_ttl_days: int = 30

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
