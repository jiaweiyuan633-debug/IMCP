import asyncio
import json

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
async def test_concurrent_duplicate_task_only_one_succeeds() -> None:
    """并发同 task_no 提交只有一方落库成功（SET NX 原子去重，无 check-then-set 竞态）。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    async def submit() -> int:
        try:
            await manager.create_task(TaskCreateRequest(task_no="dup", biz_type="job", params={}))
            return 200
        except HTTPException as exc:
            return exc.status_code

    results = await asyncio.gather(submit(), submit())
    assert sorted(results) == [200, 409]

    # 落库的任务被消费且只执行一次，无重复实例
    await _wait(manager, ["dup"])
    task = await manager.get_task("dup")
    assert task.status == "SUCCEEDED"
    assert len(service.calls) == 1


@pytest.mark.asyncio
async def test_unsupported_biz_type_rejected() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(), services={"job": _FakeService()})
    with pytest.raises(HTTPException) as exc_info:
        await manager.create_task(TaskCreateRequest(task_no="bad", biz_type="not_exist", params={}))
    assert exc_info.value.status_code == 400


@pytest.mark.asyncio
async def test_timeout_clamped_to_max() -> None:
    """超时治理——建单时把超上限的 timeout 裁剪到 max_timeout_seconds。

    修复前 request.timeout 原样入库：客户端可提交一年级的超大超时，工作协程被
    长时间占用（worker_count 有限，单任务拖垮整条队列），且租约（= 超时 + 宽限）
    等比放大、形同虚设。裁剪后入库值即执行上限；未超限/未指定分别保留原值与默认值。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(sleep=0.01)
    # max 取 300：高于默认 60，使「默认值不被裁剪」分支可单独验证
    manager = TaskManager(redis, Settings(worker_count=1, max_timeout_seconds=300), services={"job": service})

    capped = await manager.create_task(TaskCreateRequest(task_no="cap", biz_type="job", timeout=99999, params={}))
    assert capped.timeout == 300  # 超上限 → clamp 到 max

    normal = await manager.create_task(TaskCreateRequest(task_no="norm", biz_type="job", timeout=3, params={}))
    assert normal.timeout == 3  # 未超限 → 原样保留

    defaulted = await manager.create_task(TaskCreateRequest(task_no="def", biz_type="job", params={}))
    assert defaulted.timeout == 60  # 未指定 → 默认值，未达上限不裁剪

    # 裁剪后的任务以 clamp 值执行并正常完成
    await _wait_terminal(manager, "cap")
    assert (await manager.get_task("cap")).status == "SUCCEEDED"


@pytest.mark.asyncio
async def test_execute_clamps_legacy_huge_timeout() -> None:
    """执行侧兜底裁剪——修复前入库的超大 timeout 旧任务仍受上限约束。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(sleep=0.01)
    settings = Settings(worker_count=1, max_timeout_seconds=10)
    manager = TaskManager(redis, settings, services={"job": service})

    # 模拟修复前旧记录：绕过 create_task 裁剪，直接把超大 timeout 写入 Redis
    await manager._save("legacy", {
        "task_no": "legacy", "biz_type": "job", "status": "QUEUED", "params": {},
        "retry_count": 0, "max_retry": 3, "priority": 5, "timeout": 999999,
    })
    await manager._enqueue_ready("legacy", 5)
    # create_task 才会被动拉起 worker；直接入队须显式启动消费者
    manager._ensure_workers()

    task = await _wait_terminal(manager, "legacy")
    assert task.status == "SUCCEEDED"  # 未因 timeout 被误杀，clamp 到 10s 后正常执行
    assert service.calls == ["run"]


@pytest.mark.asyncio
async def test_unknown_provider_task_fails_non_retryable() -> None:
    """任务路径未知 provider 属参数错误，立即失败不重试。

    修复前 llm_chat/embedding 服务对未知 provider 抛 KeyError，被 TaskManager
    按可重试异常处理（重试 3 次耗尽才进死信）——参数错误重试毫无意义且浪费
    退避时间。改抛 NonRetryableError 后一次失败、reason=non_retryable。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(worker_count=1))
    await manager.create_task(TaskCreateRequest(
        task_no="bad-provider",
        biz_type="llm_chat",
        params={"messages": [{"role": "user", "content": "hi"}], "provider": "not-a-provider"},
    ))

    task = await _wait_terminal(manager, "bad-provider")
    assert task.status == "FAILED"
    assert task.reason == "non_retryable"
    assert task.retry_count == 1  # 参数错误不重试
    assert "unknown provider" in task.error


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
async def test_dead_letter_queue_capped_and_keeps_newest() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=NonRetryableError("参数非法"))
    settings = Settings(worker_count=1, queue_dead_max_len=2)
    manager = TaskManager(redis, settings, services={"job": service})

    # 三个失败任务顺序入库（zset 同分按字典序：d1 -> d2 -> d3），
    # 每次写入 rpush + ltrim 裁剪至上限 2，d1 应被挤出、保留最新的 d2/d3
    for task_no in ("d1", "d2", "d3"):
        await manager.create_task(TaskCreateRequest(task_no=task_no, biz_type="job", params={}))
    await _wait(manager, ["d1", "d2", "d3"])

    dead = await redis.lrange(settings.queue_dead_key, 0, -1)
    assert len(dead) == 2  # 死信队列长度被裁剪到上限
    remaining = {json.loads(item)["task_no"] for item in dead}
    assert remaining == {"d2", "d3"}  # 仅保留最近两条，最旧 d1 被裁剪


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
