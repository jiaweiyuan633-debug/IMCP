"""LLM 对话任务服务：biz_type=llm_chat。

入参：{"messages": [{"role","content"}], "model"?, "temperature"?, "provider"?, "mask_pii"?}
出参：{"content", "model", "provider", "usage"}
"""

from __future__ import annotations

from typing import Any

from app.pii import mask
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError


class LlmChatService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        messages = params.get("messages")
        if not isinstance(messages, list) or not messages:
            raise ValueError("messages 不能为空")
        try:
            provider = self.context.providers.get(str(params.get("provider", "mock")))
        except KeyError as exc:
            # R4-1.34：未知 provider 属参数配置错误，立即失败（non_retryable），
            # 不进入可重试分类浪费重试次数与退避时间
            raise NonRetryableError(f"unknown provider: {params.get('provider')}") from exc
        content = await provider.chat(
            messages,
            model=params.get("model"),
            temperature=params.get("temperature"),
        )
        # R4-1.34：PII 强制——任务出参默认脱敏，模型复述的敏感信息不出站
        if params.get("mask_pii", True):
            content = mask(content, self.context.settings.pii_mask_char)
        return {
            "content": content,
            "model": params.get("model"),
            "provider": getattr(provider, "name", None),
            "usage": None,
        }
