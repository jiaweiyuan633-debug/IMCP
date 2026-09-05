"""LLM 对话任务服务：biz_type=llm_chat。

入参：{"messages": [{"role","content"}], "model"?, "temperature"?, "provider"?, "mask_pii"?}
出参：{"content", "model", "provider", "usage"}
"""

from __future__ import annotations

from typing import Any

from app.pii import mask
from app.pii.outbound import mask_outbound_messages, should_mask_outbound
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
        # 入参消息尺寸上限（与 /chat 的 Pydantic 校验对齐），防止超大 payload 经
        # 任务队列撑爆 Redis 与 LLM 上下文
        if len(messages) > 100 or any(
            len(str(m.get("content", ""))) > 32_000 for m in messages
        ):
            raise ValueError("messages 数量超 100 或单条超 32k 字符")
        try:
            provider = self.context.providers.get(str(params.get("provider", "mock")))
        except KeyError as exc:
            # 未知 provider 属参数配置错误，立即失败（non_retryable），
            # 不进入可重试分类浪费重试次数与退避时间
            raise NonRetryableError(f"unknown provider: {params.get('provider')}") from exc
        # 出站 PII 脱敏：发给外部 provider 的 user 消息先脱敏。mask_pii 参数默认开；
        # 服务端强制开关（PII_MASK_REQUIRED）开启时无法关闭（mock 进程内提供方豁免）
        mask_enabled = should_mask_outbound(
            self.context.settings, provider, bool(params.get("mask_pii", True))
        )
        outbound = messages
        if mask_enabled:
            outbound = mask_outbound_messages(messages, self.context.settings.pii_mask_char)
        content = await provider.chat(
            outbound,
            model=params.get("model"),
            temperature=params.get("temperature"),
        )
        # 输出侧同样处理：模型复述的敏感信息不出站
        if mask_enabled:
            content = mask(content, self.context.settings.pii_mask_char)
        return {
            "content": content,
            "model": params.get("model"),
            "provider": getattr(provider, "name", None),
            "usage": None,
        }
