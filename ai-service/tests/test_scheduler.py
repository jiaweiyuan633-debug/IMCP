"""定时管道调度器测试：cron/interval 计算、注册、到期入队、注销、非法表达式。"""

import asyncio
import json
from datetime import UTC, datetime
from types import SimpleNamespace

import fakeredis.aioredis
import pytest

from app.core.config import Settings
from app.scheduler import Scheduler, next_cron_run, next_interval_run


class FakeTaskManager:
    """记录 create_task 调用的假任务管理器（鸭子类型，符合调度器契约）。"""

    def __init__(self) -> None:
        self.calls: list[tuple] = []

    async def create_task(self, request, request_id=None):
        self.calls.append((request, request_id))
        return SimpleNamespace(status="QUEUED")


class SelectiveFailingTaskManager(FakeTaskManager):
    """task_no 含指定调度 id 时抛异常，用于验证循环对单条失败不中断。"""

    def __init__(self, fail_id: str) -> None:
        super().__init__()
        self.fail_id = fail_id

    async def create_task(self, request, request_id=None):
        self.calls.append((request, request_id))
        if self.fail_id in request.task_no:
            raise RuntimeError("boom")
        return SimpleNamespace(status="QUEUED")


async def _wait_for_spec_run_count(
    redis, spec_key: str, id_: str, min_count: int = 1, timeout: float = 2.0
) -> None:
    """轮询 Redis 中的 spec，等待 run_count 达到 min_count。"""
    loop = asyncio.get_running_loop()
    deadline = loop.time() + timeout
    while loop.time() < deadline:
        raw = await redis.hget(spec_key, id_)
        if raw and json.loads(raw)["run_count"] >= min_count:
            return
        await asyncio.sleep(0.01)
    raise AssertionError(f"调度 {id_} 的 run_count 未达到 {min_count}")


async def _run_loop_bounded(scheduler: Scheduler, timeout: float = 0.5) -> None:
    """以超时方式运行后台循环（循环本身不退出，超时即取消）。"""
    with pytest.raises(asyncio.TimeoutError):
        await asyncio.wait_for(scheduler.run_loop(), timeout=timeout)


@pytest.mark.asyncio
async def test_next_cron_run_next_monday_9am() -> None:
    """cron \"0 9 * * 1\"：从周三出发，下一命中应为最近周一 09:00（UTC）。"""
    after = datetime(2026, 8, 5, 12, 34, 56, tzinfo=UTC).timestamp()  # 周三
    expected = datetime(2026, 8, 10, 9, 0, 0, tzinfo=UTC).timestamp()  # 周一
    assert next_cron_run("0 9 * * 1", after) == expected


@pytest.mark.asyncio
async def test_next_cron_run_dom_dow_or_semantics() -> None:
    """dom 与 dow 同时约束时按 OR 语义：\"0 9 9 * 1\" 命中周日(9日)而非周一。"""
    after = datetime(2026, 8, 8, 12, 0, 0, tzinfo=UTC).timestamp()  # 周六
    expected = datetime(2026, 8, 9, 9, 0, 0, tzinfo=UTC).timestamp()  # 周日 9 日
    assert next_cron_run("0 9 9 * 1", after) == expected


@pytest.mark.asyncio
async def test_next_interval_run() -> None:
    """间隔调度：after=1000、interval=60 → 1060。"""
    assert next_interval_run(60, 1000.0) == 1060.0


@pytest.mark.asyncio
async def test_register_writes_spec_and_due() -> None:
    """register：写入 spec hash 与 due zset，list_schedules 可见。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings()
    scheduler = Scheduler(redis, settings)
    info = await scheduler.register(
        "job", "interval:1", "text_summary", {"content": "hi"}
    )
    assert set(info) == {
        "id", "name", "schedule", "biz_type", "params", "enabled", "next_run_at", "run_count",
    }
    assert info["run_count"] == 0
    assert info["enabled"] is True

    raw = await redis.hget(settings.scheduler_spec_key, info["id"])
    spec = json.loads(raw)
    assert spec["name"] == "job"
    assert spec["schedule"] == "interval:1"
    assert spec["biz_type"] == "text_summary"
    assert spec["run_count"] == 0

    due = dict(await redis.zrange(settings.scheduler_due_key, 0, -1, withscores=True))
    assert due[info["id"]] == info["next_run_at"]
    assert due[info["id"]] > 0

    listed = await scheduler.list_schedules()
    assert len(listed) == 1
    assert listed[0]["id"] == info["id"]
    assert listed[0]["next_run_at"] == info["next_run_at"]


@pytest.mark.asyncio
async def test_run_loop_enqueues_due() -> None:
    """run_loop：到期条目被入队，task_no 形如 SCHED:{id}:1，run_count 递增并重排。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    fake_tm = FakeTaskManager()
    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(fake_tm)
    info = await scheduler.register(
        "job", "interval:1", "text_summary", {"content": "hi"}
    )
    # 把到期时间强制设为过去，确保第一轮立即触发
    await redis.zadd(settings.scheduler_due_key, {info["id"]: 0})

    await scheduler.start()
    await _wait_for_spec_run_count(redis, settings.scheduler_spec_key, info["id"], 1)
    await scheduler.stop()

    assert len(fake_tm.calls) >= 1
    request, request_id = fake_tm.calls[0]
    assert request_id is None
    assert request.task_no == f"SCHED:{info['id']}:1"
    assert request.biz_type == "text_summary"
    assert request.params == {"content": "hi"}

    spec = json.loads(await redis.hget(settings.scheduler_spec_key, info["id"]))
    assert spec["run_count"] >= 1
    # 入队成功后已重排下一次触发
    due = dict(await redis.zrange(settings.scheduler_due_key, 0, -1, withscores=True))
    assert due[info["id"]] > 0


@pytest.mark.asyncio
async def test_unregister_stops_enqueue() -> None:
    """unregister：spec 与 due 均清除，后续循环不再入队。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    fake_tm = FakeTaskManager()
    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(fake_tm)
    info = await scheduler.register("job", "interval:1", "text_summary", {})

    await scheduler.unregister(info["id"])
    assert await redis.hget(settings.scheduler_spec_key, info["id"]) is None
    assert not await redis.zrange(settings.scheduler_due_key, 0, -1)
    assert await scheduler.list_schedules() == []

    await _run_loop_bounded(scheduler)
    assert fake_tm.calls == []


@pytest.mark.asyncio
async def test_run_loop_tolerates_enqueue_failure() -> None:
    """入队失败只记日志：失败调度不推进，其他调度仍正常入队，循环不中断。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    scheduler = Scheduler(redis, settings)
    fail_info = await scheduler.register("fail", "interval:1", "text_summary", {})
    ok_info = await scheduler.register("ok", "interval:1", "text_summary", {})
    fake_tm = SelectiveFailingTaskManager(fail_id=fail_info["id"])
    scheduler.attach_task_manager(fake_tm)
    await redis.zadd(settings.scheduler_due_key, {fail_info["id"]: 0, ok_info["id"]: 0})

    await scheduler.start()
    await _wait_for_spec_run_count(redis, settings.scheduler_spec_key, ok_info["id"], 1)
    await scheduler.stop()

    # 两个调度都尝试过入队，失败的未推进 run_count，成功的已推进
    assert len(fake_tm.calls) == 2
    fail_spec = json.loads(await redis.hget(settings.scheduler_spec_key, fail_info["id"]))
    ok_spec = json.loads(await redis.hget(settings.scheduler_spec_key, ok_info["id"]))
    assert fail_spec["run_count"] == 0
    assert ok_spec["run_count"] >= 1


@pytest.mark.asyncio
async def test_register_unknown_prefix_raises() -> None:
    """未知调度前缀抛 ValueError。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    scheduler = Scheduler(redis, Settings())
    with pytest.raises(ValueError):
        await scheduler.register("job", "monthly:1", "text_summary", {})


@pytest.mark.asyncio
async def test_register_invalid_interval_raises() -> None:
    """interval 秒数必须为正。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    scheduler = Scheduler(redis, Settings())
    with pytest.raises(ValueError):
        await scheduler.register("job", "interval:0", "text_summary", {})


@pytest.mark.asyncio
async def test_register_invalid_cron_field_raises() -> None:
    """cron 字段取值越界（dow 7 不在 0-6）抛 ValueError。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    scheduler = Scheduler(redis, Settings())
    with pytest.raises(ValueError):
        await scheduler.register("job", "cron:0 9 * * 7", "text_summary", {})
