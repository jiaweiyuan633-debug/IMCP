"""定时管道调度器。

设计：以 Redis zset（scheduler_due_key）作为到期队列，score 为下次触发时间（unix ts）；
调度定义存于 hash（scheduler_spec_key）。后台循环（run_loop）扫描到期条目，
逐个入队任务（复用任务管理器的 create_task 鸭子类型接口），入队成功后推进 run_count
并计算下一次触发时间，重新写回 zset。

触发表达式：
  - "interval:N"：每隔 N 秒触发一次；
  - "cron:M H DOM MON DOW"：标准 cron 5 段表达式（DOW 0-6，0=周日）。
"""

import asyncio
import json
import logging
import time
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

from redis import Redis

from app.core.config import Settings
from app.schemas.task import TaskCreateRequest

logger = logging.getLogger(__name__)

# cron 各字段的取值范围与最小步长
_CRON_FIELDS = (
    ("minute", 0, 59),
    ("hour", 0, 23),
    ("day-of-month", 1, 31),
    ("month", 1, 12),
    ("day-of-week", 0, 6),
)


def next_interval_run(interval_seconds: int, after: float) -> float:
    """间隔调度：下一次触发时间 = after + 间隔秒数。"""
    return after + interval_seconds


def _parse_cron_field(field: str, lo: int, hi: int) -> set[int]:
    """解析 cron 单字段，返回允许取值的集合。

    支持：*、数字、逗号列表、a/b 步进（可选扩展）。
    """
    values: set[int] = set()
    for raw in field.split(","):
        raw = raw.strip()
        if not raw:
            raise ValueError(f"cron 字段含空段: {field!r}")
        if raw == "*":
            values.update(range(lo, hi + 1))
        elif "/" in raw:
            base, _, step_s = raw.partition("/")
            if not step_s.isdigit() or int(step_s) <= 0:
                raise ValueError(f"cron 步进无效: {raw!r}")
            step = int(step_s)
            if base == "*":
                start, stop = lo, hi
            elif base.isdigit():
                start, stop = int(base), hi
            else:
                raise ValueError(f"cron 步进基数无效: {raw!r}")
            values.update(range(start, stop + 1, step))
        elif raw.isdigit():
            values.add(int(raw))
        else:
            raise ValueError(f"cron 字段值无效: {raw!r}")
    if not values:
        raise ValueError(f"cron 字段无有效取值: {field!r}")
    if not all(lo <= v <= hi for v in values):
        raise ValueError(f"cron 字段取值越界（{lo}-{hi}）: {field!r}")
    return values


def _parse_cron_expr(expr: str) -> tuple[set[int], ...]:
    """解析 5 段 cron 表达式，返回各字段允许取值集合；格式或取值非法抛 ValueError。"""
    parts = expr.split()
    if len(parts) != 5:
        raise ValueError(f"cron 表达式需要 5 段（分 时 日 月 周）: {expr!r}")
    return tuple(_parse_cron_field(part, lo, hi) for part, (_, lo, hi) in zip(parts, _CRON_FIELDS))


def next_cron_run(expr: str, after: float) -> float:
    """cron 调度：返回 after 之后第一个匹配的触发时间（unix ts，按 UTC 计算）。

    dom 与 dow 的约束规则与标准 cron 一致：两者都为 * 时天天命中；
    仅一方为 * 时由另一方约束；两者都约束时按 OR 语义（任一匹配即命中）。
    最多向后查找 366 天，无匹配抛 ValueError。
    """
    parts = expr.split()
    minutes, hours, doms, months, dows = _parse_cron_expr(expr)
    dom_wild = parts[2].strip() == "*"
    dow_wild = parts[4].strip() == "*"
    # 从 after 所在分钟边界起迭代，取严格晚于 after 的整分钟触发点
    start = datetime.fromtimestamp(after, tz=UTC).replace(second=0, microsecond=0)
    max_minutes = 366 * 24 * 60
    for offset in range(max_minutes):
        dt = start + timedelta(minutes=offset)
        if dt.timestamp() <= after:
            continue
        if dt.month not in months:
            continue
        day_matches = dt.day in doms
        cron_dow = (dt.weekday() + 1) % 7  # weekday(): 周一=0...周日=6；cron DOW: 0=周日
        dow_matches = cron_dow in dows
        if dom_wild and dow_wild:
            day_ok = True
        elif dom_wild:  # 仅 dow 约束
            day_ok = dow_matches
        elif dow_wild:  # 仅 dom 约束
            day_ok = day_matches
        else:  # dom 与 dow 均约束 → OR 语义
            day_ok = day_matches or dow_matches
        if not day_ok:
            continue
        if dt.hour not in hours:
            continue
        if dt.minute not in minutes:
            continue
        return dt.timestamp()
    raise ValueError(f"cron 表达式在 366 天内无匹配时间: {expr!r}")


def _parse_schedule(schedule: str) -> dict:
    """解析触发表达式，返回 {"kind": ...} 结构；未知前缀或非法取值抛 ValueError。"""
    prefix, _, rest = schedule.partition(":")
    if prefix == "interval":
        try:
            interval_seconds = int(rest)
        except ValueError:
            raise ValueError(f"interval 秒数无效: {schedule!r}") from None
        if interval_seconds <= 0:
            raise ValueError(f"interval 秒数必须为正: {schedule!r}")
        return {"kind": "interval", "interval_seconds": interval_seconds}
    if prefix == "cron":
        expr = rest.strip()
        _parse_cron_expr(expr)  # 注册期即校验格式与取值范围
        return {"kind": "cron", "expr": expr}
    raise ValueError(f"未知的调度前缀（仅支持 interval: 与 cron:）: {schedule!r}")


def _next_run_after(schedule: str, after: float) -> float:
    """根据触发表达式计算 after 之后的触发时间。"""
    parsed = _parse_schedule(schedule)
    if parsed["kind"] == "interval":
        return next_interval_run(parsed["interval_seconds"], after)
    return next_cron_run(parsed["expr"], after)


class Scheduler:
    """定时管道调度器：注册/注销调度，后台循环驱动到期任务入队。"""

    def __init__(self, redis: Redis, settings: Settings) -> None:
        self.redis = redis
        self.settings = settings
        self._task_manager: Any = None
        self._loop_task: asyncio.Task | None = None

    def attach_task_manager(self, task_manager: Any) -> None:
        """挂载任务管理器（鸭子类型：create_task(request, request_id=None)）。

        未挂载时 enqueue 仅记日志、不实际创建任务。
        """
        self._task_manager = task_manager

    async def register(
        self,
        name: str,
        schedule: str,
        biz_type: str,
        params: dict,
        enabled: bool = True,
    ) -> dict:
        """注册一个调度：写 spec hash 并加入到期队列，返回调度信息。

        批次3（R4-1.49）：注册期校验 biz_type 已注册（否则该调度每次触发都因
        未知任务类型入队失败、且因调度器不重排而永久停摆）。
        """
        _parse_schedule(schedule)  # 非法表达式在此抛 ValueError
        # 批次3：biz_type 校验——任务管理器支持时校验（鸭子类型：有 _service_names 即校验），
        # 测试的 FakeTaskManager 无该方法时跳过
        if (
            self._task_manager is not None
            and hasattr(self._task_manager, "_service_names")
            and biz_type not in self._task_manager._service_names()
        ):
            raise ValueError(f"未知任务类型: {biz_type}，请先注册对应服务")
        id_ = uuid4().hex[:8]
        now = time.time()
        next_ts = _next_run_after(schedule, now)
        spec = {
            "id": id_,
            "name": name,
            "schedule": schedule,
            "biz_type": biz_type,
            "params": params,
            "enabled": enabled,
            "run_count": 0,
            # 批次3：失败退避状态——入队失败后按指数退避重排，避免瞬时故障导致调度永久停摆
            "fail_count": 0,
        }
        await self.redis.hset(
            self.settings.scheduler_spec_key,
            id_,
            json.dumps(spec, ensure_ascii=False),
        )
        await self.redis.zadd(self.settings.scheduler_due_key, {id_: next_ts})
        return {**spec, "next_run_at": next_ts}

    async def unregister(self, id_: str) -> None:
        """注销一个调度：同时清除 spec 与到期队列中的条目。"""
        await self.redis.hdel(self.settings.scheduler_spec_key, id_)
        await self.redis.zrem(self.settings.scheduler_due_key, id_)

    async def list_schedules(self) -> list[dict]:
        """列出全部调度，附上到期队列中的下一次触发时间（未排期则为 None）。"""
        specs = await self.redis.hgetall(self.settings.scheduler_spec_key)
        due = await self.redis.zrange(
            self.settings.scheduler_due_key, 0, -1, withscores=True
        )
        due_map = {member: score for member, score in due}
        result: list[dict] = []
        for id_, raw in specs.items():
            spec = json.loads(raw)
            spec["next_run_at"] = due_map.get(id_)
            result.append(spec)
        return result

    async def _process_due(self, id_: str, now: float) -> None:
        """处理单个到期调度：读 spec → 入队 → 推进 run_count → 重排下次触发。

        批次3（R4-1.49）修复「单次瞬时失败即永久停摆」：原实现入队失败直接 return——
        due 条目已被 run_loop 移除、run_count 未推进、下次触发时间从未写回，该调度
        从此静默死亡。现在失败按指数退避重排 due（fail_count 递增，退避 2^n 秒封顶
        60s），恢复后自然继续；409（同名任务已存在）视为幂等成功——调度器周期触发
        天然幂等，同名任务存在即说明本轮已入队，不应按失败重排。未知 biz_type 在
        register 期已校验，此处兜底按不可恢复失败告警并重排（避免死循环）。
        """
        raw = await self.redis.hget(self.settings.scheduler_spec_key, id_)
        if raw is None:
            logger.warning("调度 %s 已不存在，跳过", id_)
            return
        spec = json.loads(raw)
        if not spec["enabled"]:
            logger.info("调度 %s 已禁用，跳过入队", id_)
            return
        task_no = f"SCHED:{id_}:{spec['run_count'] + 1}"
        try:
            request = TaskCreateRequest(
                task_no=task_no,
                biz_type=spec["biz_type"],
                params=spec["params"],
            )
            await self._task_manager.create_task(request, request_id=None)
        except Exception as exc:  # noqa: BLE001 - 单条失败不应中断循环
            if getattr(exc, "status_code", None) == 409:
                # 幂等成功：同名任务已存在（含上次触发遗留），本轮已入队——推进 run_count
                # 使下次触发使用新 task_no，并清零失败计数（批次3）
                spec["run_count"] += 1
                spec["fail_count"] = 0
            else:
                spec["fail_count"] = int(spec.get("fail_count", 0)) + 1
                logger.error(
                    "调度 %s 入队失败（第 %s 次），按指数退避重排: %s",
                    id_, spec["fail_count"], exc,
                )
            await self.redis.hset(
                self.settings.scheduler_spec_key, id_, json.dumps(spec, ensure_ascii=False)
            )
            backoff = min(2 ** min(spec["fail_count"], 6), 60.0)
            await self.redis.zadd(self.settings.scheduler_due_key, {id_: now + backoff})
            return
        spec["run_count"] += 1
        spec["fail_count"] = 0
        await self.redis.hset(
            self.settings.scheduler_spec_key, id_, json.dumps(spec, ensure_ascii=False)
        )
        # 仅在 enabled 且表达式有效时重排，否则该调度自然淡出
        try:
            next_ts = _next_run_after(spec["schedule"], now)
        except ValueError as exc:
            logger.error("调度 %s 表达式无效，停止重排: %s", id_, exc)
            return
        await self.redis.zadd(self.settings.scheduler_due_key, {id_: next_ts})

    async def run_loop(self) -> None:
        """后台主循环：扫到期条目出队处理；无到期时按 poll_seconds 休眠。"""
        while True:
            now = time.time()
            due_ids = await self.redis.zrangebyscore(
                self.settings.scheduler_due_key, 0, now
            )
            if not due_ids:
                await asyncio.sleep(self.settings.scheduler_poll_seconds)
                continue
            # R4-1.30：多副本领取互斥——zrangebyscore 只读，两实例可读到同一批到期项；
            # zrem 返回实际删除数，仅删除到（>0）的实例拥有这批的处置权，另一实例跳过。
            # 若不判 removed，两实例都会 _process_due 并各自 create_task，调度任务被重复触发。
            removed = await self.redis.zrem(self.settings.scheduler_due_key, *due_ids)
            if not removed:
                continue
            for id_ in due_ids:
                await self._process_due(id_, now)

    async def start(self) -> None:
        """启动后台循环；已运行则忽略。"""
        if self._loop_task is not None and not self._loop_task.done():
            return
        self._loop_task = asyncio.create_task(self.run_loop())
        self._loop_task.add_done_callback(
            lambda task: task.cancelled()
            or logger.error("调度后台循环意外退出: %s", task.exception())
        )

    async def stop(self) -> None:
        """停止后台循环：取消任务并等待其退出。"""
        if self._loop_task is None:
            return
        self._loop_task.cancel()
        try:
            await self._loop_task
        except asyncio.CancelledError:
            pass
        self._loop_task = None
