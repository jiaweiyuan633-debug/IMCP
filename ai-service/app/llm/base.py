"""大模型提供方抽象：对话（流式/非流式）、工具调用、文本 Embedding。

实现约定：
- 输入消息统一为 OpenAI 兼容结构：``{"role": "user", "content": "..."}``；
  工具调用返回的消息额外包含 ``tool_calls``（角色 assistant）或 ``role: tool``。
- 所有方法均为 async，调用方统一 await，便于流式逐 token 返回。
- 失败统一抛 ``LLMError``（网络/上游 5xx 等可重试）或 ``LLMConfigError``（认证/模型不存在等不可重试）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Protocol


class LLMError(RuntimeError):
    """可重试的上游异常（网络错误、超时、上游 5xx）。"""


class LLMConfigError(ValueError):
    """不可重试的配置/认证异常（401/403/404、模型不存在、未配置 key）。"""


@dataclass
class ToolCall:
    name: str
    arguments: dict = field(default_factory=dict)


@dataclass
class LLMResponse:
    content: str | None
    tool_calls: list[ToolCall] = field(default_factory=list)
    model: str | None = None
    usage: dict | None = None


class LLMProvider(Protocol):
    """统一的大模型提供方接口（真实实现与测试 Mock 均实现本协议）。"""

    name: str

    async def chat(
        self,
        messages: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> str:
        """非流式对话补全，返回完整回复文本。"""

    def stream(
        self,
        messages: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> AsyncIterator[str]:
        """流式对话补全，逐段产出 token delta（通常为单个 token 或若干字符）。"""

    async def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        model: str | None = None,
        temperature: float | None = None,
    ) -> LLMResponse:
        """带工具声明的对话补全：模型可返回最终文本或一个/多个工具调用。"""

    async def embed(self, texts: list[str], model: str | None = None) -> list[list[float]]:
        """将一批文本向量化，返回等长向量列表（维数由实现决定）。"""


class Embedder(Protocol):
    """仅具备文本向量化能力的轻量接口（RAG 管道只依赖它，不依赖完整对话能力）。"""

    async def embed(self, texts: list[str], model: str | None = None) -> list[list[float]]: ...
