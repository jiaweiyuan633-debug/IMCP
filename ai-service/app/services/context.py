"""任务服务运行时上下文：把 Redis / 配置 / 模型提供方 / 向量存储 / 调度器注入服务。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from redis.asyncio import Redis

from app.core.config import Settings
from app.llm.registry import ProviderRegistry
from app.vectors.store import RedisVectorStore


@dataclass
class ServiceContext:
    redis: Redis
    settings: Settings
    providers: ProviderRegistry
    vectors: RedisVectorStore
    scheduler: Any = field(default=None)  # app.scheduler.service.Scheduler，延迟 attach

    def embedder(self, provider: str | None = None):
        return self.providers.embedder(provider)
