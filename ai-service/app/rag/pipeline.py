"""RAG 真向量检索管道：文档入库（ingest）→ 语义检索（retrieve）。

只依赖上层接口：``Embedder``（app.llm.base）负责向量化，``RedisVectorStore``
（app.vectors.store）负责存储与余弦检索。按 ``tenant_id:base_id`` 命名空间隔离。

出域脱敏：文档内容与检索 query 最终都会交给外部 Embedding provider 向量化，
调用方可注入 ``mask_text``（如 app.pii.mask）在入库分块前 / 检索向量化前脱敏。
"""

from __future__ import annotations

from collections.abc import Callable

from app.core.threads import run_cpu
from app.llm.base import Embedder
from app.rag.chunker import chunk_document
from app.vectors.store import RedisVectorStore


class RagPipeline:
    """RAG 检索管道：把文档分块向量化入库，再按语义相似度检索。"""

    def __init__(
        self,
        store: RedisVectorStore,
        embedder: Embedder,
        mask_text: Callable[[str], str] | None = None,
    ) -> None:
        self.store = store
        self.embedder = embedder
        # 出域脱敏回调（同步函数）；None 表示不脱敏（纯本地 Mock 联调可关闭）
        self.mask_text = mask_text

    def _mask(self, text: str) -> str:
        return self.mask_text(text) if self.mask_text is not None else text

    @staticmethod
    def namespace(tenant_id: int, base_id: int) -> str:
        """租户级命名空间，隔离不同租户/知识库的向量。"""
        return f"{tenant_id}:{base_id}"

    async def ingest(
        self,
        tenant_id: int,
        base_id: int,
        docs: list[dict],
        max_chars: int = 500,
        overlap: int = 50,
    ) -> dict:
        """把一批文档分块向量化后写入向量库。

        ``docs`` 元素形如 ``{"doc_id", "title", "content"}``；每个 chunk 以
        ``doc_id:chunk_index`` 为 doc_id 写入，payload 携带完整元信息。
        返回 ``{"chunks": 分块总数, "docs": 文档数}``。
        """
        namespace = self.namespace(tenant_id, base_id)
        total_chunks = 0
        for doc in docs:
            doc_id = str(doc.get("doc_id", ""))
            # 文档内容先脱敏再分块/向量化：向量库与 payload 均不落原文
            content = self._mask(str(doc.get("content", "")))
            # 分块是 CPU 密集（长文本正则切割），放有界线程池避免阻塞事件循环
            chunks = await run_cpu(
                chunk_document,
                str(doc.get("title", "")),
                content,
                max_chars,
                overlap,
            )
            if not chunks:
                continue
            vectors = await self.embedder.embed([c["content"] for c in chunks])
            for chunk, vector in zip(chunks, vectors):
                await self.store.upsert(
                    namespace,
                    f"{doc_id}:{chunk['chunk_index']}",
                    vector,
                    payload={
                        "title": chunk["title"],
                        "content": chunk["content"],
                        "doc_id": doc_id,
                        "chunk_index": chunk["chunk_index"],
                        "tenant_id": tenant_id,
                        "base_id": base_id,
                    },
                )
            total_chunks += len(chunks)
        return {"chunks": total_chunks, "docs": len(docs)}

    async def retrieve(
        self,
        tenant_id: int,
        base_id: int,
        query: str,
        top_k: int = 5,
    ) -> list[dict]:
        """语义检索：向量化 query 后返回相似度最高的若干 chunk。

        命中项含 ``doc_id``/``score``/``payload``；query 向量为空（空串等）返回 []。
        """
        # 检索 query 同样出域到 embedding provider，向量化前脱敏
        vectors = await self.embedder.embed([self._mask(query)])
        if not vectors:
            return []
        query_vector = vectors[0]
        if not query_vector or all(value == 0.0 for value in query_vector):
            return []
        return await self.store.search(self.namespace(tenant_id, base_id), query_vector, top_k)
