"""轻量 ML 任务服务：biz_type=ml_classify / ml_cluster。

- ml_classify train：{"action": "train", "name", "labels": [...], "docs": [...]}
- ml_classify predict：{"action": "predict", "name", "doc", "k"?}
- ml_cluster：{"docs": [...], "k"?, "max_iter"?}
"""

from __future__ import annotations

import asyncio
from typing import Any

from app.ml import KMeansTextClusterer, load_model, train_model
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError


class MlClassifyService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        name = params.get("name")
        if not name:
            raise ValueError("模型 name 必填")
        if params.get("action") == "train":
            labels = params.get("labels")
            docs = params.get("docs")
            if not isinstance(labels, list) or not isinstance(docs, list) or not labels or not docs:
                raise ValueError("训练需要非空 labels 与 docs")
            if len(labels) != len(docs):
                raise ValueError("labels 与 docs 长度必须一致")
            # 批次3（R4-1.49）：TF-IDF 训练是 CPU 密集，移入线程池
            return await asyncio.to_thread(train_model, self.context.redis, name, labels, docs)
        model = await load_model(self.context.redis, name)
        if model is None:
            raise NonRetryableError(f"模型不存在: {name}")
        return {"name": name, **model.predict(params.get("doc", ""), k=int(params.get("k", 3)))}


class MlClusterService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        docs = params.get("docs")
        if not isinstance(docs, list) or not docs:
            raise ValueError("docs 不能为空")
        k = int(params.get("k", 2))
        if k < 2 or k > len(docs):
            raise ValueError(f"k 必须在 [2, {len(docs)}] 之间")
        # 批次3（R4-1.49）：KMeans 迭代是 CPU 密集，移入线程池
        result = await asyncio.to_thread(
            KMeansTextClusterer().cluster,
            docs,
            k=k,
            max_iter=int(params.get("max_iter", 20)),
        )
        return result
