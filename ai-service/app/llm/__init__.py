"""大模型层：真实 LLM（OpenAI 兼容多供应商）、流式、工具调用、文本 Embedding。"""

from app.llm.base import Embedder, LLMConfigError, LLMError, LLMProvider, LLMResponse, ToolCall
from app.llm.mock import MockProvider
from app.llm.openai_compat import OpenAICompatibleProvider
from app.llm.registry import ProviderRegistry, build_registry

__all__ = [
    "Embedder",
    "LLMConfigError",
    "LLMError",
    "LLMProvider",
    "LLMResponse",
    "MockProvider",
    "OpenAICompatibleProvider",
    "ProviderRegistry",
    "ToolCall",
    "build_registry",
]
