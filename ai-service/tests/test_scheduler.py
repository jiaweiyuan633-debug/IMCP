"""定时管道调度器测试：cron/interval 计算、注册、到期入队、注销、非法表达式。"""

import asyncio
import json
import time
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


class ConflictTaskManager(FakeTaskManager):
    """create_task 恒抛 409（同名任务已存在）——验证调度器按幂等成功处理。"""

    async def create_task(self, request, request_id=None):
        self.calls.append((request, request_id))
        from fastapi import HTTPException

        raise HTTPException(status_code=409, detail="task already exists")


@pytest.mark.asyncio
async def test_run_loop_treats_409_as_idempotent_success() -> None:
    """create_task 抛 409（同名任务已存在）按幂等成功处理——run_count 推进、
    fail_count 清零、正常重排，而非按失败退避。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    conflict_tm = ConflictTaskManager()
    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(conflict_tm)
    info = await scheduler.register("job", "interval:1", "text_summary", {})
    await redis.zadd(settings.scheduler_due_key, {info["id"]: 0})

    await scheduler.start()
    await _wait_for_spec_run_count(redis, settings.scheduler_spec_key, info["id"], 1)
    await scheduler.stop()

    spec = json.loads(await redis.hget(settings.scheduler_spec_key, info["id"]))
    assert spec["run_count"] >= 1
    assert spec["fail_count"] == 0
    # 409 后仍重排（与成功路径一致），调度不淡出
    due = dict(await redis.zrange(settings.scheduler_due_key, 0, -1, withscores=True))
    assert info["id"] in due


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
    # spec 新增 fail_count（失败退避状态）
    assert set(info) == {
        "id", "name", "schedule", "biz_type", "params", "enabled", "next_run_at", "run_count", "fail_count",
    }
    assert info["run_count"] == 0
    assert info["fail_count"] == 0
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
    """入队失败只记日志：失败调度按指数退避重排（不永久停摆），其他调度仍正常入队，循环不中断。"""
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

    # 两个调度都尝试过入队，失败的未推进 run_count 但 fail_count 递增并重排
    assert len(fake_tm.calls) == 2
    fail_spec = json.loads(await redis.hget(settings.scheduler_spec_key, fail_info["id"]))
    ok_spec = json.loads(await redis.hget(settings.scheduler_spec_key, ok_info["id"]))
    assert fail_spec["run_count"] == 0
    assert fail_spec["fail_count"] >= 1
    assert ok_spec["run_count"] >= 1
    # 失败调度已被重排（fail_count 退避 2s），不会自然淡出
    due = dict(await redis.zrange(settings.scheduler_due_key, 0, -1, withscores=True))
    assert fail_info["id"] in due
    assert due[fail_info["id"]] > 0


@pytest.mark.asyncio
async def test_run_loop_claim_is_atomic_under_two_instances() -> None:
    """多副本领取互斥——同一批到期项只被一个实例入队，杜绝调度任务重复触发。

    修复前 run_loop 的 zrangebyscore（只读）+ zrem 不判返回值：两实例可读到同一批
    到期项并各自 _process_due → 同名任务 create_task 两次（调度重复触发）。
    修复后仅 zrem 删除到（>0）的实例持有处置权。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    fake_tm = FakeTaskManager()
    sched_a = Scheduler(redis, settings)
    sched_a.attach_task_manager(fake_tm)
    sched_b = Scheduler(redis, settings)
    sched_b.attach_task_manager(fake_tm)
    info = await sched_a.register("job", "interval:60", "text_summary", {})
    await redis.zadd(settings.scheduler_due_key, {info["id"]: 0})  # 立即到期

    # 两实例并发跑后台循环（循环不退出，超时即取消）；interval:60 触发一轮后重排到
    # 未来 60s、不再有到期项。若领取非原子，同一到期项会被两实例各入队一次。
    with pytest.raises(asyncio.TimeoutError):
        await asyncio.wait_for(
            asyncio.gather(sched_a.run_loop(), sched_b.run_loop()), timeout=0.5
        )

    spec = json.loads(await redis.hget(settings.scheduler_spec_key, info["id"]))
    assert spec["run_count"] == 1
    task_nos = [req.task_no for req, _ in fake_tm.calls]
    assert task_nos.count(f"SCHED:{info['id']}:1") == 1


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


# ---------- 崩溃一致性：对账补排 + 循环容错 ----------


@pytest.mark.asyncio
async def test_reconcile_reschedules_enabled_spec_missing_due() -> None:
    """崩溃窗口（spec 已写、due 未写）对账补排：enabled 补排、disabled 跳过。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    scheduler = Scheduler(redis, settings)
    enabled_id = (await scheduler.register("job", "interval:3600", "text_summary", {}))["id"]
    disabled_id = (
        await scheduler.register("off", "interval:3600", "text_summary", {}, enabled=False)
    )["id"]
    # 模拟「zrem 出队后崩溃 / register 中途崩溃」：spec 在、due 缺失
    await redis.zrem(settings.scheduler_due_key, enabled_id, disabled_id)
    assert not await redis.zrange(settings.scheduler_due_key, 0, -1)

    rescheduled = await scheduler.reconcile_due()

    assert rescheduled == 1  # 仅 enabled 补排
    due = dict(await redis.zrange(settings.scheduler_due_key, 0, -1, withscores=True))
    assert enabled_id in due
    assert due[enabled_id] > time.time()  # 补排到未来触发点
    assert disabled_id not in due


@pytest.mark.asyncio
async def test_start_reconciles_then_loop_enqueues() -> None:
    """start 前对账补排缺失条目，随后 run_loop 正常触发入队。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    fake_tm = FakeTaskManager()
    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(fake_tm)
    info = await scheduler.register("job", "interval:1", "text_summary", {})
    await redis.zrem(settings.scheduler_due_key, info["id"])

    await scheduler.start()
    await _wait_for_spec_run_count(redis, settings.scheduler_spec_key, info["id"], 1)
    await scheduler.stop()
    assert len(fake_tm.calls) >= 1


@pytest.mark.asyncio
async def test_run_loop_survives_transient_redis_error() -> None:
    """Redis 抖动不杀死调度循环：退避后继续消费到期条目。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    fake_tm = FakeTaskManager()
    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(fake_tm)
    info = await scheduler.register("job", "interval:1", "text_summary", {})
    await redis.zadd(settings.scheduler_due_key, {info["id"]: 0})

    real_zrange = redis.zrangebyscore
    attempts = {"n": 0}

    async def flaky_zrangebyscore(*args, **kwargs):
        attempts["n"] += 1
        if attempts["n"] == 1:
            raise ConnectionError("模拟 Redis 抖动")
        return await real_zrange(*args, **kwargs)

    redis.zrangebyscore = flaky_zrangebyscore  # type: ignore[method-assign]

    await scheduler.start()
    await _wait_for_spec_run_count(redis, settings.scheduler_spec_key, info["id"], 1)
    assert attempts["n"] >= 1
    assert len(fake_tm.calls) >= 1
    await scheduler.stop()


@pytest.mark.asyncio
async def test_run_loop_requeues_failed_item_and_continues() -> None:
    """单条处理遇 Redis 故障：该调度按退避写回 due，其余调度照常入队，循环不退出。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    settings = Settings(scheduler_poll_seconds=0.01)
    fake_tm = FakeTaskManager()
    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(fake_tm)
    ok_info = await scheduler.register("ok", "interval:1", "text_summary", {})
    fail_info = await scheduler.register("fail", "interval:1", "text_summary", {})
    await redis.zadd(settings.scheduler_due_key, {ok_info["id"]: 0, fail_info["id"]: 0})

    real_hget = redis.hget
    triggered = {"done": False}

    async def flaky_hget(*args, **kwargs):
        # 对 fail 调度读取 spec 时抛一次连接错误（模拟处理中途 Redis 抖动）
        if (
            not triggered["done"]
            and len(args) >= 2
            and args[0] == settings.scheduler_spec_key
            and args[1] == fail_info["id"]
        ):
            triggered["done"] = True
            raise ConnectionError("模拟 Redis 抖动")
        return await real_hget(*args, **kwargs)

    redis.hget = flaky_hget  # type: ignore[method-assign]

    await scheduler.start()
    await _wait_for_spec_run_count(redis, settings.scheduler_spec_key, ok_info["id"], 1)
    await scheduler.stop()

    # ok 调度正常入队推进；fail 调度被写回 due（score 在未来）
    assert len(fake_tm.calls) >= 1
    due = dict(await redis.zrange(settings.scheduler_due_key, 0, -1, withscores=True))
    assert fail_info["id"] in due
    assert due[fail_info["id"]] > 0
