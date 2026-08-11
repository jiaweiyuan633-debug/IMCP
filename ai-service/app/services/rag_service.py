"""RAG 任务服务：biz_type=rag_ingest / rag_retrieve。

- rag_ingest：{"tenant_id", "base_id", "docs": [{"doc_id","title","content"}], "max_chars"?, "overlap"?}
- rag_retrieve：{"tenant_id", "base_id", "query", "top_k"?}
"""

from __future__ import annotations

from typing import Any

from app.rag import RagPipeline
from app.services.base import BaseTaskService
from app.services.context import ServiceContext


class RagIngestService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context
        self.pipeline = RagPipeline(context.vectors, context.embedder())

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        tenant_id = int(params.get("tenant_id"))
        base_id = int(params.get("base_id"))
        docs = params.get("docs")
        if not isinstance(docs, list) or not docs:
            raise ValueError("docs 不能为空，格式 [{'doc_id','title','content'}]")
        return await self.pipeline.ingest(
            tenant_id,
            base_id,
            docs,
            max_chars=int(params.get("max_chars", 500)),
            overlap=int(params.get("overlap", 50)),
        )


class RagRetrieveService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context
        self.pipeline = RagPipeline(context.vectors, context.embedder())

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        tenant_id = int(params.get("tenant_id"))
        base_id = int(params.get("base_id"))
        query = params.get("query")
        if not query:
            raise ValueError("query 不能为空")
        hits = await self.pipeline.retrieve(tenant_id, base_id, query, top_k=int(params.get("top_k", 5)))
        return {"hits": hits, "count": len(hits)}
