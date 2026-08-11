"""LLM 对话任务服务：biz_type=llm_chat。

入参：{"messages": [{"role","content"}], "model"?, "temperature"?, "provider"?}
出参：{"content", "model", "provider", "usage"}
"""

from __future__ import annotations

from typing import Any

from app.services.base import BaseTaskService
from app.services.context import ServiceContext


class LlmChatService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        messages = params.get("messages")
        if not isinstance(messages, list) or not messages:
            raise ValueError("messages 不能为空")
        provider = self.context.providers.get(str(params.get("provider", "mock")))
        content = await provider.chat(
            messages,
            model=params.get("model"),
            temperature=params.get("temperature"),
        )
        return {
            "content": content,
            "model": params.get("model"),
            "provider": getattr(provider, "name", None),
            "usage": None,
        }
