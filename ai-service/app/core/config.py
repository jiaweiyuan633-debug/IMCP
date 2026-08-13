from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ai-service"
    redis_url: str = "redis://localhost:6379/0"
    # 共享鉴权密钥：入站任务接口校验（Authorization: Bearer）与出站回调 HMAC 签名共用，
    # 必须与后端 AiServiceConfig.apiKey 保持一致。无默认值：未注入 AUTH_TOKEN 时构造
    # Settings 即抛错、服务启动失败（fail-fast），杜绝静默携带公开 dev 密钥上线；
    # 本地开发由 .env（.env.example 模板）显式提供，生产由 k8s Secret 注入。
    auth_token: str
    # 回调 HMAC 时间戳允许偏差（秒），防重放
    callback_clock_skew_seconds: int = 300
    # 出站任务回调允许的 origin 白名单（JSON 数组，如 ["http://admin-backend:8080"]）：
    # 配置后回调地址必须与其中一项 origin 精确匹配；留空则仅允许 localhost/127.0.0.1
    # 回环地址（本地开发）。无论何种模式，云元数据（169.254.169.254）与链路本地/保留
    # 地址一律拒绝（SSRF 防护，见 app/core/callback_security.py）。生产必须显式注入。
    callback_allowed_origins: list[str] = []
    default_timeout_seconds: int = 60
    # 内部服务默认不开放跨域；如需浏览器直连，显式配置允许来源
    cors_origins: list[str] = []

    # ---------- 大模型（真实 LLM + 多供应商） ----------
    # 默认提供方：mock（确定性降级实现，开箱即用、测试可重复）；
    # 生产通过 LLM_PROVIDERS 配置一个或多个 OpenAI 兼容提供方（OpenAI/DeepSeek/通义/Ollama/vLLM 等）
    llm_default_provider: str = "mock"
    # 提供方字典：{"deepseek": {"base_url": "https://api.deepseek.com",
    #   "api_key": "sk-xxx", "model": "deepseek-chat", "embedding_model": "..."}}
    # 环境变量以 JSON 字符串注入（LLM_PROVIDERS），pydantic-settings 自动解析
    llm_providers: dict[str, dict] = {}
    llm_timeout_seconds: int = 120

    # ---------- 向量存储（Redis 精确余弦；scaffold 规模够用，大规模换 Milvus/pgvector） ----------
    vector_namespace_prefix: str = "ai:vec"
    vector_default_dim: int = 384

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

    # ---------- PII 脱敏 ----------
    pii_mask_char: str = "*"

    # ---------- 定时管道 ----------
    scheduler_spec_key: str = "ai:sched:spec"
    scheduler_due_key: str = "ai:sched:due"
    scheduler_poll_seconds: float = 1.0
    scheduler_ttl_days: int = 30

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
