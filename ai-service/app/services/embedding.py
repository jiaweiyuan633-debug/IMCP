"""文本 Embedding 任务服务：biz_type=embedding。

入参：{"texts": [...], "model"?, "provider"?, "mask_pii"?}
出参：{"vectors", "dim", "model", "provider"}
"""

from __future__ import annotations

from typing import Any

from app.pii.outbound import mask_outbound_texts, should_mask_outbound
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError


class EmbeddingService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        texts = params.get("texts")
        if not isinstance(texts, list) or not texts:
            raise ValueError("texts 不能为空")
        if len(texts) > 200 or any(len(str(t)) > 32_000 for t in texts):
            raise ValueError("texts 数量超 200 或单条超 32k 字符")
        try:
            provider = self.context.providers.get(str(params.get("provider", "mock")))
        except KeyError as exc:
            # 未知 provider 属参数配置错误，立即失败，不浪费重试次数
            raise NonRetryableError(f"unknown provider: {params.get('provider')}") from exc
        # 待向量化文本出域到外部 provider 前先脱敏（同一出口，见 app.pii.outbound）
        if should_mask_outbound(
            self.context.settings, provider, bool(params.get("mask_pii", True))
        ):
            texts = mask_outbound_texts(texts, self.context.settings.pii_mask_char)
        vectors = await provider.embed(texts, model=params.get("model"))
        return {
            "vectors": vectors,
            "dim": len(vectors[0]) if vectors else 0,
            "model": params.get("model"),
            "provider": getattr(provider, "name", None),
        }
