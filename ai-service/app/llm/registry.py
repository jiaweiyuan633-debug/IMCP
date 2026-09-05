"""多供应商注册表：按名称解析 LLM 提供方，提供默认提供方与 Embedding 入口。

默认注册确定性 ``mock`` 提供方（无外部依赖）；配置了 ``LLM_PROVIDERS`` 时，
每个条目注册为一个 OpenAI 兼容提供方（OpenAI/DeepSeek/通义/Ollama 等共用同一协议）。
"""

from __future__ import annotations

from typing import cast

from app.core.config import Settings
from app.llm.base import Embedder, LLMProvider
from app.llm.mock import MockProvider
from app.llm.openai_compat import OpenAICompatibleProvider


class ProviderRegistry:
    def __init__(self) -> None:
        self._providers: dict[str, LLMProvider] = {}
        self._default: str = "mock"

    def register(self, name: str, provider: LLMProvider) -> None:
        self._providers[name] = provider

    def set_default(self, name: str) -> None:
        if name not in self._providers:
            raise KeyError(f"未注册提供方: {name}")
        self._default = name

    def names(self) -> list[str]:
        return sorted(self._providers)

    def get(self, name: str) -> LLMProvider:
        if name not in self._providers:
            raise KeyError(f"未注册提供方: {name}")
        return self._providers[name]

    def default(self) -> LLMProvider:
        return self._providers[self._default]

    def embedder(self, name: str | None = None) -> Embedder:
        provider = self.get(name) if name else self.default()
        return cast(Embedder, provider)

    async def aclose_all(self) -> None:
        """应用关闭时释放全部提供方持有的连接池。

        仅对提供方公开 ``aclose`` 生命周期方法（OpenAICompatibleProvider 持久
        复用连接池；MockProvider 等无连接资源，跳过）。启动失败前也可能已构造
        部分提供方，遍历全部幂等释放。
        """
        for provider in self._providers.values():
            closer = getattr(provider, "aclose", None)
            if closer is not None:
                await closer()


def build_registry(settings: Settings) -> ProviderRegistry:
    registry = ProviderRegistry()
    registry.register("mock", MockProvider())
    for name, config in (settings.llm_providers or {}).items():
        registry.register(
            name,
            OpenAICompatibleProvider(
                base_url=config.base_url,
                api_key=config.api_key,
                default_model=config.model,
                embedding_model=config.embedding_model,
                timeout_seconds=config.timeout_seconds or settings.llm_timeout_seconds,
                name=name,
            ),
        )
    registry.set_default(settings.llm_default_provider or "mock")
    # 生产防呆（LLM_FAIL_FAST）：默认提供方解析为 mock（= 未配置任何真实
    # LLM_PROVIDERS）即启动失败，防止生产静默跑 mock 假数据而不自知
    if settings.llm_fail_fast and registry.default().name == "mock":
        raise RuntimeError(
            "LLM_FAIL_FAST=true 但默认提供方为 mock：请配置 LLM_PROVIDERS 并设置 "
            "LLM_DEFAULT_PROVIDER，或关闭 LLM_FAIL_FAST（仅限本地开发）"
        )
    return registry
