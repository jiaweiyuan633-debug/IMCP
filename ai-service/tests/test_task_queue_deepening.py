import asyncio
import json
from unittest.mock import AsyncMock

import fakeredis.aioredis
import pytest
from fastapi import HTTPException

from app.core.config import Settings
from app.schemas.task import TaskCreateRequest
from app.tasks.errors import NonRetryableError
from app.tasks.manager import TaskManager


class _FakeService:
    """可编程假服务：记录调用顺序 / 抛异常 / 可 sleep。"""

    def __init__(self, exc: Exception | None = None, sleep: float = 0.0) -> None:
        self.exc = exc
        self.sleep = sleep
        self.calls: list[str] = []
        self.order: list[str] = []

    async def run(self, params: dict) -> dict:
        self.calls.append(params.get("tag", "run"))
        self.order.append(params.get("tag", "run"))
        if self.sleep:
            await asyncio.sleep(self.sleep)
        if self.exc:
            raise self.exc
        return {"ok": True, "tag": params.get("tag")}


@pytest.mark.asyncio
async def test_priority_queue_higher_priority_runs_first() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(sleep=0.05)
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    await manager.create_task(TaskCreateRequest(task_no="low", biz_type="job", priority=1, params={"tag": "low"}))
    await manager.create_task(TaskCreateRequest(task_no="high", biz_type="job", priority=9, params={"tag": "high"}))

    await _wait(manager, ["low", "high"])
    assert service.order == ["high", "low"]


@pytest.mark.asyncio
async def test_duplicate_task_rejected_with_409() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(), services={"job": _FakeService()})
    await manager.create_task(TaskCreateRequest(task_no="dup", biz_type="job", params={}))
    with pytest.raises(HTTPException) as exc_info:
        await manager.create_task(TaskCreateRequest(task_no="dup", biz_type="job", params={}))
    assert exc_info.value.status_code == 409


@pytest.mark.asyncio
async def test_unsupported_biz_type_rejected() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(), services={"job": _FakeService()})
    with pytest.raises(HTTPException) as exc_info:
        await manager.create_task(TaskCreateRequest(task_no="bad", biz_type="not_exist", params={}))
    assert exc_info.value.status_code == 400


@pytest.mark.asyncio
async def test_timeout_fails_without_retry() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(sleep=5)
    manager = TaskManager(redis, Settings(worker_count=1), services={"slow": service})
    await manager.create_task(TaskCreateRequest(task_no="t", biz_type="slow", timeout=0.1, params={}))

    task = await _wait_terminal(manager, "t")
    assert task.status == "FAILED"
    assert "timeout" in task.error
    assert task.retry_count == 1  # 超时不可重试，仅失败一次
    assert len(service.calls) == 1


@pytest.mark.asyncio
async def test_non_retryable_error_fails_immediately() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=NonRetryableError("参数非法"))
    manager = TaskManager(redis, Settings(worker_count=1, retry_backoff_seconds=0.01), services={"job": service})
    await manager.create_task(TaskCreateRequest(task_no="n", biz_type="job", params={}))

    task = await _wait_terminal(manager, "n")
    assert task.status == "FAILED"
    assert len(service.calls) == 1  # 不可重试：不重试
    assert task.retry_count == 1


@pytest.mark.asyncio
async def test_retryable_error_exhausts_retries_then_dead_letter() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=RuntimeError("上游抖动"))
    settings = Settings(worker_count=1, retry_backoff_seconds=0.01, task_max_retry=2)
    manager = TaskManager(redis, settings, services={"job": service})
    await manager.create_task(TaskCreateRequest(task_no="r", biz_type="job", params={}))

    task = await _wait_terminal(manager, "r")
    assert task.status == "FAILED"
    assert task.retry_count == 2  # 达到 max_retry=2 后失败
    assert len(service.calls) == 2

    dead = await redis.lrange(settings.queue_dead_key, 0, -1)
    assert any("r" in json.loads(item)["task_no"] for item in dead)


@pytest.mark.asyncio
async def test_retry_manual_requeues_failed_task() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=RuntimeError("首次失败"))
    manager = TaskManager(redis, Settings(worker_count=1, retry_backoff_seconds=0.01), services={"job": service})
    await manager.create_task(TaskCreateRequest(task_no="m", biz_type="job", params={}))
    await _wait_terminal(manager, "m")

    # 换一个会成功的服务，手动重试
    service.exc = None
    retried = await manager.retry("m")
    assert retried.status == "QUEUED"

    task = await _wait_terminal(manager, "m", attempts=100)
    assert task.status == "SUCCEEDED"


@pytest.mark.asyncio
async def test_close_cancels_workers() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(sleep=5)
    manager = TaskManager(redis, Settings(worker_count=2), services={"slow": service})
    await manager.create_task(TaskCreateRequest(task_no="c", biz_type="slow", params={}))
    await asyncio.sleep(0.05)
    assert len(manager._workers) == 2
    await manager.close()
    assert len(manager._workers) == 0


async def _wait_terminal(manager: TaskManager, task_no: str, attempts: int = 150):
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        task = await manager.get_task(task_no)
        if task is not None and task.status in ("SUCCEEDED", "FAILED"):
            return task
    raise AssertionError(f"任务 {task_no} 未在预期时间内到达终态")


async def _wait(manager: TaskManager, expected: list[str], attempts: int = 200):
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        states = [await manager.get_task(t) for t in expected]
        if all(s is not None and s.status in ("SUCCEEDED", "FAILED") for s in states):
            return
    raise AssertionError(f"任务 {expected} 未在预期时间内到达终态")
