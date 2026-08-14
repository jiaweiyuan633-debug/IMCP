"""R4-1.6：任务队列自愈——worker 容错 + RUNNING 遗留恢复。

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
    """R4-1.30：进程重启后遗留 QUEUED 任务即使无新提交也有消费者（ensure_workers 主动拉起）。

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


async def _wait_terminal(manager: TaskManager, task_no: str, attempts: int = 200):
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        task = await manager.get_task(task_no)
        if task is not None and task.status in ("SUCCEEDED", "FAILED"):
            return task
    raise AssertionError(f"任务 {task_no} 未在预期时间内到达终态")
