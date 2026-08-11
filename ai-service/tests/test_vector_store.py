import fakeredis.aioredis
import pytest

from app.vectors import RedisVectorStore, VectorDimensionError, cosine, dot, normalize


def test_cosine_basics() -> None:
    assert cosine([1, 0], [1, 0]) == pytest.approx(1.0)
    assert cosine([1, 0], [0, 1]) == pytest.approx(0.0)
    assert cosine([0, 0], [1, 1]) == 0.0  # 零向量不报错返回 0
    assert dot([1, 2], [3, 4]) == 11
    assert normalize([3, 4]) == [pytest.approx(0.6), pytest.approx(0.8)]


@pytest.mark.asyncio
async def test_upsert_and_search_returns_sorted_by_cosine() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    store = RedisVectorStore(redis)
    await store.upsert("kb", "a", [1.0, 0.0, 0.0], payload={"title": "甲"})
    await store.upsert("kb", "b", [1.0, 1.0, 0.0], payload={"title": "乙"})
    await store.upsert("kb", "c", [0.0, 0.0, 1.0], payload={"title": "丙"})

    # 余弦：q·a=1/1.118≈0.894，q·b=1.5/1.581≈0.949，q·c=0
    hits = await store.search("kb", [1.0, 0.5, 0.0], top_k=2)

    assert [h["doc_id"] for h in hits] == ["b", "a"]
    assert hits[0]["payload"]["title"] == "乙"
    assert hits[0]["score"] > hits[1]["score"]


@pytest.mark.asyncio
async def test_threshold_filters_low_scores() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    store = RedisVectorStore(redis)
    await store.upsert("kb", "a", [1.0, 0.0, 0.0])
    await store.upsert("kb", "b", [0.0, 0.0, 1.0])

    hits = await store.search("kb", [1.0, 0.0, 0.0], threshold=0.5)
    assert [h["doc_id"] for h in hits] == ["a"]


@pytest.mark.asyncio
async def test_dimension_mismatch_rejected() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    store = RedisVectorStore(redis)
    await store.upsert("kb", "a", [1.0, 0.0, 0.0])

    with pytest.raises(VectorDimensionError):
        await store.upsert("kb", "b", [1.0, 0.0])


@pytest.mark.asyncio
async def test_delete_and_delete_namespace() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    store = RedisVectorStore(redis)
    await store.upsert("kb", "a", [1.0, 0.0])
    await store.upsert("kb", "b", [0.0, 1.0])
    assert await store.size("kb") == 2

    await store.delete("kb", "a")
    assert await store.size("kb") == 1

    await store.delete_namespace("kb")
    assert await store.size("kb") == 0
    assert await store.search("kb", [1.0, 0.0]) == []


@pytest.mark.asyncio
async def test_search_empty_namespace() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    store = RedisVectorStore(redis)
    assert await store.search("none", [1.0, 0.0]) == []
