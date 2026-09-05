"""任务队列自愈——worker 容错 + RUNNING 遗留恢复。

覆盖：
- worker 遇瞬时错误（Redis 抖动）不退出，退避后继续消费；
- 执行期异常把可能遗留 RUNNING 的任务恢复为 QUEUED 重新入队；
- 启动扫描回收租约已过期的 RUNNING 遗留任务，且不误伤未过期租约/已终态任务。
"""

import asyncio
import json
import time

import fakeredis.aioredis
import pytest

from app.core.config import Settings
from app.schemas.task import TaskCreateRequest
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


@pytest.mark.asyncio
async def test_worker_survives_transient_redis_error() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    real_zpopmin = redis.zpopmin
    attempts = {"n": 0}

    async def flaky_zpopmin(*args, **kwargs):
        attempts["n"] += 1
        if attempts["n"] == 1:
            raise ConnectionError("模拟 Redis 抖动")
        return await real_zpopmin(*args, **kwargs)

    redis.zpopmin = flaky_zpopmin  # type: ignore[method-assign]

    await manager.create_task(TaskCreateRequest(task_no="s", biz_type="job", params={}))

    task = await _wait_terminal(manager, "s")
    assert task.status == "SUCCEEDED"
    assert service.calls == 1
    assert len(manager._workers) == 1  # 瞬时错误未杀死消费线程


@pytest.mark.asyncio
async def test_startup_recovery_requeues_stale_running_task() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    base = {
        "biz_type": "job",
        "status": "RUNNING",
        "params": {},
        "callback_url": None,
        "request_id": None,
        "result": None,
        "error": None,
        "retry_count": 1,
        "max_retry": 3,
        "priority": 5,
        "timeout": 30,
        "created_at": "2026-08-13T00:00:00+00:00",
        "updated_at": "2026-08-13T00:00:00+00:00",
        "started_at": "2026-08-13T00:00:00+00:00",
    }
    # 崩溃遗留：RUNNING + 租约已过期，且不在任何队列中
    stale = {**base, "task_no": "stale-1", "lease_until": time.time() - 10}
    await redis.set("ai:task:stale-1", json.dumps(stale))
    # 租约未过期：可能仍被其它实例执行，不应回收
    live = {**base, "task_no": "live-1", "lease_until": time.time() + 600}
    await redis.set("ai:task:live-1", json.dumps(live))
    # 已终态：不应回收
    done = {**base, "task_no": "done-1", "status": "SUCCEEDED"}
    await redis.set("ai:task:done-1", json.dumps(done))

    recovered = await manager.recover_stale_tasks()

    assert recovered == 1
    data = json.loads(await redis.get("ai:task:stale-1"))
    assert data["status"] == "QUEUED"
    assert data["error"] == "recovered after worker interruption"

    # 重新入队后由 worker 执行成功
    manager._ensure_workers()
    task = await _wait_terminal(manager, "stale-1")
    assert task.status == "SUCCEEDED"
    assert service.calls == 1


@pytest.mark.asyncio
async def test_ensure_workers_consumes_leftover_after_restart() -> None:
    """进程重启后遗留 QUEUED 任务即使无新提交也有消费者（ensure_workers 主动拉起）。

    修复前 worker 仅在 create_task/retry 时被动启动：重启后队列有遗留任务但无人提交
    新任务，_ensure_workers 永不触发，遗留任务永久停滞。lifespan 启动调用 ensure_workers。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    # 模拟重启前的残留：任务已落库并入队，但 worker 从未启动（无消费者）
    leftover = {
        "task_no": "leftover",
        "biz_type": "job",
        "status": "QUEUED",
        "params": {},
        "callback_url": None,
        "request_id": None,
        "result": None,
        "error": None,
        "retry_count": 0,
        "max_retry": 3,
        "priority": 5,
        "timeout": 30,
        "created_at": "2026-08-13T00:00:00+00:00",
        "updated_at": "2026-08-13T00:00:00+00:00",
    }
    await manager._save("leftover", leftover)
    await manager._enqueue_ready("leftover", 5)
    assert not manager._workers  # 尚无消费者

    manager.ensure_workers()
    assert len(manager._workers) == 1

    task = await _wait_terminal(manager, "leftover")
    assert task.status == "SUCCEEDED"
    assert service.calls == 1
    await manager.close()


@pytest.mark.asyncio
async def test_execute_error_requeues_inflight_task() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    real_save = manager._save
    failures = {"left": 1}

    async def flaky_save(task_no: str, data: dict) -> None:
        if data.get("status") == "SUCCEEDED" and failures["left"] > 0:
            failures["left"] -= 1
            raise ConnectionError("模拟成功落库瞬时失败")
        await real_save(task_no, data)

    manager._save = flaky_save  # type: ignore[method-assign]

    await manager.create_task(TaskCreateRequest(task_no="e", biz_type="job", params={}))

    task = await _wait_terminal(manager, "e")
    assert task.status == "SUCCEEDED"
    assert service.calls == 2  # 首次执行后落库失败被重入队，第二次执行成功
    assert failures["left"] == 0
    assert len(manager._workers) == 1  # 执行期错误同样未杀死消费线程


@pytest.mark.asyncio
async def test_recover_requeues_queued_orphan_not_in_any_queue() -> None:
    """「QUEUED 但不在任何队列」的孤儿被启动自愈回收：重入队后执行成功。

    覆盖 create_task 落库与入队之间崩溃的窗口（补偿删除失败 / 进程被杀）——
    修复前 recover 只回收 RUNNING，此类孤儿无人消费、永久停滞。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    orphan = {
        "task_no": "orphan-1",
        "biz_type": "job",
        "status": "QUEUED",
        "params": {},
        "callback_url": None,
        "request_id": None,
        "result": None,
        "error": None,
        "retry_count": 0,
        "max_retry": 3,
        "priority": 5,
        "timeout": 30,
        "created_at": "2026-08-13T00:00:00+00:00",
        "updated_at": "2026-08-13T00:00:00+00:00",  # 足够旧，越过孤儿宽限
    }
    await manager._save("orphan-1", orphan)
    assert not await redis.zscore(settings_queue_ready(manager), "orphan-1")

    recovered = await manager.recover_stale_tasks()

    assert recovered == 1
    assert await redis.zscore(settings_queue_ready(manager), "orphan-1") is not None
    manager._ensure_workers()
    task = await _wait_terminal(manager, "orphan-1")
    assert task.status == "SUCCEEDED"
    assert service.calls == 1


@pytest.mark.asyncio
async def test_recover_skips_queued_task_still_in_queue() -> None:
    """正常 QUEUED（在 ready 队列中）不当作孤儿回收。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})
    await manager.create_task(TaskCreateRequest(task_no="in-queue", biz_type="job", params={}))
    await manager._get("in-queue")

    # worker 已消费完任务，任务变为终态；再造一条 QUEUED 且仍在队列中的记录
    queued = {
        "task_no": "queued-kept",
        "biz_type": "job",
        "status": "QUEUED",
        "params": {},
        "retry_count": 0,
        "max_retry": 3,
        "priority": 5,
        "timeout": 30,
        "updated_at": "2026-08-13T00:00:00+00:00",
    }
    await manager._save("queued-kept", queued)
    await manager._enqueue_ready("queued-kept", 5)

    recovered = await manager.recover_stale_tasks()
    assert recovered == 0  # 在队列中的 QUEUED 不被回收


@pytest.mark.asyncio
async def test_create_task_enqueue_failure_deletes_record() -> None:
    """建单补偿：入队失败时删除刚落库的任务，不留孤儿。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService()
    manager = TaskManager(redis, Settings(worker_count=1), services={"job": service})

    real_zadd = redis.zadd

    async def failing_zadd(*args, **kwargs):
        raise ConnectionError("模拟入队瞬时失败")

    redis.zadd = failing_zadd  # type: ignore[method-assign]
    with pytest.raises(ConnectionError):
        await manager.create_task(TaskCreateRequest(task_no="comp", biz_type="job", params={}))
    redis.zadd = real_zadd  # type: ignore[method-assign]

    # 补偿删除：落库记录不存在，不会遗留 QUEUED 孤儿
    assert await manager._get("comp") is None


@pytest.mark.asyncio
async def test_retry_enqueue_failure_rolls_back_state() -> None:
    """手动重试入队失败：状态回滚到原终态，不留在 QUEUED 中间态。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    service = _FakeService(exc=RuntimeError("先失败一次"))
    settings = Settings(worker_count=1, retry_backoff_seconds=0.01)
    manager = TaskManager(redis, settings, services={"job": service})
    await manager.create_task(TaskCreateRequest(task_no="rb", biz_type="job", params={}))
    await _wait_terminal(manager, "rb")
    assert (await manager.get_task("rb")).status == "FAILED"

    real_zadd = redis.zadd

    async def failing_zadd(*args, **kwargs):
        raise ConnectionError("模拟入队瞬时失败")

    redis.zadd = failing_zadd  # type: ignore[method-assign]
    with pytest.raises(ConnectionError):
        await manager.retry("rb")
    redis.zadd = real_zadd  # type: ignore[method-assign]

    task = await manager.get_task("rb")
    assert task.status == "FAILED"  # 回滚到重试前的终态
    assert task.reason == "retries_exhausted"


def settings_queue_ready(manager: TaskManager) -> str:
    return manager.settings.queue_ready_key


async def _wait_terminal(manager: TaskManager, task_no: str, attempts: int = 200):
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        task = await manager.get_task(task_no)
        if task is not None and task.status in ("SUCCEEDED", "FAILED"):
            return task
    raise AssertionError(f"任务 {task_no} 未在预期时间内到达终态")
