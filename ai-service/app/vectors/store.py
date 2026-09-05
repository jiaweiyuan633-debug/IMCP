"""Redis 向量存储：按命名空间精确余弦检索。

存储结构：
- ``{prefix}:{namespace}``   —— Hash：doc_id → JSON{vector, payload}
- ``{prefix}:ns:{namespace}`` —— Set：命名空间下全部 doc_id（用于整库删除与计数）

检索采用加载后全量精确计算（scaffold 规模足够，保证召回质量）；检索前先检查
命名空间大小上限（vector_search_max_vectors），超限拒绝——避免一次性 hgetall
超大命名空间撑爆内存/线程。维度在首次写入时自动探测并锁定，维度不一致的写入
会被拒绝，避免检索结果无意义。
"""

from __future__ import annotations

import json
from typing import Any

from redis.asyncio import Redis

from app.core.config import settings
from app.core.threads import run_cpu
from app.vectors.linalg import cosine


class VectorDimensionError(ValueError):
    """向量维度与命名空间已锁定维度不一致。"""


class NamespaceTooLargeError(ValueError):
    """命名空间向量数超过精确检索上限（改用专用向量库或缩小命名空间）。"""


class RedisVectorStore:
    def __init__(self, redis: Redis, prefix: str = "ai:vec") -> None:
        self.redis = redis
        self.prefix = prefix

    def _key(self, namespace: str) -> str:
        return f"{self.prefix}:{namespace}"

    def _ns_key(self, namespace: str) -> str:
        return f"{self.prefix}:ns:{namespace}"

    async def upsert(self, namespace: str, doc_id: str, vector: list[float], payload: dict | None = None) -> None:
        dim = len(vector)
        existing = await self.redis.hget(self._key(namespace), doc_id)
        if existing:
            stored = json.loads(existing)
            if len(stored["vector"]) != dim:
                raise VectorDimensionError(f"维度不一致: 已存 {len(stored['vector'])}，写入 {dim}")
        elif dim != 0:
            # 首次写入锁定维度：任取一条已存向量比对
            sample = await self._sample_dimension(namespace)
            if sample is not None and sample != dim:
                raise VectorDimensionError(f"维度不一致: 命名空间 {sample}，写入 {dim}")
        entry = json.dumps({"vector": vector, "payload": payload or {}}, ensure_ascii=False)
        await self.redis.hset(self._key(namespace), doc_id, entry)
        await self.redis.sadd(self._ns_key(namespace), doc_id)

    async def delete(self, namespace: str, doc_id: str) -> None:
        await self.redis.hdel(self._key(namespace), doc_id)
        await self.redis.srem(self._ns_key(namespace), doc_id)

    async def delete_namespace(self, namespace: str) -> None:
        await self.redis.delete(self._key(namespace), self._ns_key(namespace))

    async def size(self, namespace: str) -> int:
        return await self.redis.scard(self._ns_key(namespace))

    async def search(
        self,
        namespace: str,
        query_vector: list[float],
        top_k: int = 5,
        threshold: float = 0.0,
    ) -> list[dict[str, Any]]:
        # 命名空间大小保护：精确检索需全量加载逐条算余弦，超大命名空间会一次性
        # hgetall 撑爆内存/线程——超限显式拒绝，提示换专用向量库
        size = await self.redis.scard(self._ns_key(namespace))
        limit = settings.vector_search_max_vectors
        if size > limit:
            raise NamespaceTooLargeError(
                f"命名空间 {namespace} 含 {size} 条向量，超过精确检索上限 {limit}，"
                "请改用 Milvus/pgvector 等向量库或缩小命名空间"
            )
        raw = await self.redis.hgetall(self._key(namespace))
        # 余弦扫描是 CPU 密集（逐条 JSON 解码 + 内积循环），放有界线程池执行
        return await run_cpu(self._score_all, raw, query_vector, top_k, threshold)

    def _score_all(
        self,
        raw: dict[bytes | str, bytes | str],
        query_vector: list[float],
        top_k: int,
        threshold: float,
    ) -> list[dict[str, Any]]:
        scored: list[tuple[float, dict[str, Any]]] = []
        for doc_id, entry_json in raw.items():
            entry = json.loads(entry_json)
            score = cosine(query_vector, entry["vector"])
            if score >= threshold:
                scored.append((score, {"doc_id": doc_id, "score": round(score, 6), "payload": entry.get("payload", {})}))
        scored.sort(key=lambda item: item[0], reverse=True)
        return [item[1] for item in scored[: max(top_k, 0)]]

    async def _sample_dimension(self, namespace: str) -> int | None:
        doc_id = await self.redis.srandmember(self._ns_key(namespace))
        if not doc_id:
            return None
        entry_json = await self.redis.hget(self._key(namespace), doc_id)
        if not entry_json:
            return None
        return len(json.loads(entry_json)["vector"])
