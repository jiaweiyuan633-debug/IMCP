"""轻量 ML 任务服务：biz_type=ml_classify / ml_cluster。

- ml_classify train：{"action": "train", "name", "labels": [...], "docs": [...]}
- ml_classify predict：{"action": "predict", "name", "doc", "k"?}
- ml_cluster：{"docs": [...], "k"?, "max_iter"?}
"""

from __future__ import annotations

from typing import Any

from app.core.threads import run_cpu
from app.ml import KMeansTextClusterer, load_model, train_model
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError

# ml_cluster 输入规模上限：KMeans 迭代是 CPU 密集且线程内不可中断，超大输入会
# 长时间占用有界线程池。按总字符数预估，超限直接拒绝（NonRetryableError）。
ML_CLUSTER_MAX_CHARS = 5_000_000


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
            # train_model 是 async 函数：CPU 密集的 fit 在其内部经有界线程池执行、
            # Redis 写入回到事件循环 await。此处直接 await，绝不交给 to_thread
            # （to_thread 提交协程函数只会得到永不执行的协程对象）。
            return await train_model(self.context.redis, name, labels, docs)
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
        if sum(len(str(doc)) for doc in docs) > ML_CLUSTER_MAX_CHARS:
            raise NonRetryableError(
                f"聚类输入超过 {ML_CLUSTER_MAX_CHARS // 1_000_000}MB 字符上限，请分批处理"
            )
        # KMeans 迭代是 CPU 密集且线程内不可中断：经有界线程池执行（并发上限
        # cpu_thread_pool_size），配合上面的输入规模预检控制线程占用。
        result = await run_cpu(
            KMeansTextClusterer().cluster,
            docs,
            k=k,
            max_iter=int(params.get("max_iter", 20)),
        )
        return result
