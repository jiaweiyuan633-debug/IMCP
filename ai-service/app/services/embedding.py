"""文本 Embedding 任务服务：biz_type=embedding。

入参：{"texts": [...], "model"?, "provider"?}
出参：{"vectors", "dim", "model", "provider"}
"""

from __future__ import annotations

from typing import Any

from app.services.base import BaseTaskService
from app.services.context import ServiceContext


class EmbeddingService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        texts = params.get("texts")
        if not isinstance(texts, list) or not texts:
            raise ValueError("texts 不能为空")
        provider = self.context.providers.get(str(params.get("provider", "mock")))
        vectors = await provider.embed(texts, model=params.get("model"))
        return {
            "vectors": vectors,
            "dim": len(vectors[0]) if vectors else 0,
            "model": params.get("model"),
            "provider": getattr(provider, "name", None),
        }
