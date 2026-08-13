"""R4-1.8：应用级 Prometheus 指标（业务计数 + 队列深度）。

覆盖：
- 任务生命周期打点：创建/成功/失败（按 reason 标签）/重试 计数正确递增；
- 队列深度 gauge 抓取时实时采样反映 Redis 真实水位，Redis 不可用/未初始化置 -1；
- 工作协程数 gauge 随启动/停机更新；
- /metrics 端点输出包含自定义业务指标，且不因 Redis 故障 500。
"""

import asyncio

import fakeredis.aioredis
import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.core import metrics
from app.core.config import Settings, settings
from app.schemas.task import TaskCreateRequest
from app.tasks.errors import NonRetryableError
from app.tasks.manager import TaskManager


class _FakeService:
    """可编程假服务：记录调用次数，可抛异常。"""

    def __init__(self, exc: Exception | None = None) -> None:
        self.exc = exc
        self.calls = 0

    async def run(self, params: dict) -> dict:
        self.calls += 1
        if self.exc:
            raise self.exc
        return {"ok": True}


def _metric_value(metric, labels: dict[str, str] | None = None) -> float:
    """读取指标当前值：无标签取总和；有标签按标签子集匹配（各标签组合唯一样本）。

    用公开的 collect() 而非解析文本，避免依赖 generate_latest 对零值计数器的
    渲染行为。prometheus_client 对带标签的 Counter 会额外产出 ``<name>_created``
    样本（值为该标签组合首次出现的创建时间戳），须剔除，否则时间戳会被误加进
    总和。计数器进程内全局累加，故测试一律取增量断言。
    """
    total = 0.0
    for family in metric.collect():
        for sample in family.samples:
            if sample.name.endswith("_created"):
                continue  # 计数器创建时间戳样本（值为时间），非计数值
            if labels is None or all(sample.labels.get(k) == v for k, v in labels.items()):
                total += sample.value
    return total


# ---------- 任务生命周期计数 ----------


@pytest.mark.asyncio
async def test_non_retryable_failure_increments_created_and_failed() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=NonRetryableError("参数非法"))
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    before_created = _metric_value(metrics.ai_task_created_total)
    before_failed = _metric_value(metrics.ai_task_failed_total, {"reason": "non_retryable"})
    before_succeeded = _metric_value(metrics.ai_task_succeeded_total)

    await manager.create_task(TaskCreateRequest(task_no="n1", biz_type="job", params={}))
    task = await _wait_terminal(manager, "n1")

    assert task.status == "FAILED"
    assert service.calls == 1
    assert _metric_value(metrics.ai_task_created_total) == before_created + 1
    assert _metric_value(metrics.ai_task_failed_total, {"reason": "non_retryable"}) == before_failed + 1
    assert _metric_value(metrics.ai_task_succeeded_total) == before_succeeded  # 未成功不计入


@pytest.mark.asyncio
async def test_retry_exhaustion_increments_retried_and_exhausted() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=RuntimeError("上游抖动"))
    manager = TaskManager(
        redis,
        Settings(worker_count=1, retry_backoff_seconds=0.01, task_max_retry=2),
        services={"job": service},
    )

    before_retried = _metric_value(metrics.ai_task_retried_total)
    before_exhausted = _metric_value(metrics.ai_task_failed_total, {"reason": "retries_exhausted"})

    await manager.create_task(TaskCreateRequest(task_no="r1", biz_type="job", params={}))
    task = await _wait_terminal(manager, "r1")

    assert task.status == "FAILED"
    assert task.retry_count == 2
    assert service.calls == 2
    # 第一次失败（retry_count 1 < max 2）→ 入延迟队列重试一次；第二次失败重试耗尽
    assert _metric_value(metrics.ai_task_retried_total) == before_retried + 1
    assert _metric_value(metrics.ai_task_failed_total, {"reason": "retries_exhausted"}) == before_exhausted + 1


@pytest.mark.asyncio
async def test_success_increments_succeeded() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    before_succeeded = _metric_value(metrics.ai_task_succeeded_total)

    await manager.create_task(TaskCreateRequest(task_no="s1", biz_type="job", params={}))
    task = await _wait_terminal(manager, "s1")

    assert task.status == "SUCCEEDED"
    assert _metric_value(metrics.ai_task_succeeded_total) == before_succeeded + 1


# ---------- 队列深度 gauge ----------


@pytest.mark.asyncio
async def test_queue_depth_gauge_reflects_redis_watermark() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    # 直接种三条队列的真实水位（与 sample_queue_depth 使用同一份全局 settings 键名）
    await redis.zadd(settings.queue_ready_key, {"t1": -5, "t2": -9})
    await redis.zadd(settings.queue_delayed_key, {"t3": 1_000_000})
    await redis.rpush(settings.queue_dead_key, "d1-payload")

    await metrics.sample_queue_depth(redis)

    assert _metric_value(metrics.ai_queue_depth.labels(queue="ready")) == 2
    assert _metric_value(metrics.ai_queue_depth.labels(queue="delayed")) == 1
    assert _metric_value(metrics.ai_queue_depth.labels(queue="dead")) == 1


@pytest.mark.asyncio
async def test_queue_depth_marks_unknown_when_redis_unavailable() -> None:
    class _BrokenRedis:
        async def zcard(self, *args):
            raise ConnectionError("redis down")

        async def llen(self, *args):
            raise ConnectionError("redis down")

    await metrics.sample_queue_depth(_BrokenRedis())
    assert _metric_value(metrics.ai_queue_depth.labels(queue="ready")) == -1
    assert _metric_value(metrics.ai_queue_depth.labels(queue="delayed")) == -1
    assert _metric_value(metrics.ai_queue_depth.labels(queue="dead")) == -1

    # 应用未初始化完成（redis 为 None）同样置未知而非抛错
    await metrics.sample_queue_depth(None)
    assert _metric_value(metrics.ai_queue_depth.labels(queue="ready")) == -1


# ---------- 工作协程数 gauge ----------


@pytest.mark.asyncio
async def test_worker_count_gauge_tracks_lifecycle() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(worker_count=2), services={"job": _FakeService()})

    manager._ensure_workers()
    assert _metric_value(metrics.ai_worker_count) == 2

    await manager.close()
    assert _metric_value(metrics.ai_worker_count) == 0


# ---------- /metrics 端点 ----------

# 让 lifespan 里的 Redis.from_url(...) 返回 fakeredis，避免依赖真实 Redis
main_module.Redis = fakeredis.aioredis.FakeRedis


def _boot_client() -> TestClient:
    return TestClient(main_module.app)


def test_metrics_endpoint_exposes_business_metrics() -> None:
    # 保证计数器在文本输出中可见（不依赖「零值计数器是否渲染」的实现细节）
    metrics.ai_task_created_total.inc()
    with _boot_client() as client:
        response = client.get("/metrics")
    assert response.status_code == 200
    body = response.text
    assert "ai_task_created_total" in body
    assert "ai_queue_depth" in body
    assert "ai_worker_count" in body


async def _wait_terminal(manager: TaskManager, task_no: str, attempts: int = 200):
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        task = await manager.get_task(task_no)
        if task is not None and task.status in ("SUCCEEDED", "FAILED"):
            return task
    raise AssertionError(f"任务 {task_no} 未在预期时间内到达终态")
