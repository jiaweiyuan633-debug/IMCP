from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ai-service"
    redis_url: str = "redis://localhost:6379/0"
    # 共享鉴权密钥：入站任务接口校验（Authorization: Bearer）与出站回调 HMAC 签名共用，
    # 必须与后端 AiServiceConfig.apiKey 保持一致；生产由 k8s Secret 注入（AUTH_TOKEN）
    auth_token: str = "dev-ai-service-token"
    # 回调 HMAC 时间戳允许偏差（秒），防重放
    callback_clock_skew_seconds: int = 300
    default_timeout_seconds: int = 60
    # 内部服务默认不开放跨域；如需浏览器直连，显式配置允许来源
    cors_origins: list[str] = []

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
