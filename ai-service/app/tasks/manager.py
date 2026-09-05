"""任务队列深化：优先级 + 超时 + 可重试分类 + 去重 + 死信 + 有界工作池。

队列结构（Redis）：
- ``ai:queue:ready``     zset，score = -priority（优先级越大越先执行；同优先级按任务号字典序）
- ``ai:queue:delayed``   zset，score = 就绪时间戳（重试退避到期后由工作线程提升到 ready）
- ``ai:queue:dead``      list，超出重试或不可重试的失败任务（JSON 记录，写入时
                         rpush + ltrim 原子裁剪至 queue_dead_max_len 上限，防无界增长）

执行模型：固定数量工作线程（settings.worker_count）持续消费 ready 队列；
单任务执行受 ``asyncio.wait_for`` 超时保护；可重试异常按指数式/固定退避重新入队，
不可重试异常（NonRetryableError/超时）直接失败并写死信。

可观测性：任务生命周期事件同步打点（见 app.core.metrics）——创建/成功/失败（按
原因分标签）/进入重试计数，工作协程数与队列深度 gauge，供 Prometheus 抓取。
"""

import asyncio
import hashlib
import hmac
import json
import logging
import time
from datetime import UTC, datetime
from typing import Any

import httpx
from fastapi import HTTPException
from redis.asyncio import Redis

from app.core.callback_security import CallbackUrlGuard
from app.core.config import Settings
from app.core.metrics import (
    ai_task_created_total,
    ai_task_failed_total,
    ai_task_retried_total,
    ai_task_succeeded_total,
    ai_worker_count,
)
from app.core.observability import request_id_var
from app.schemas.task import DeadLetterEntry, TaskCreateRequest, TaskStatusResponse
from app.tasks.errors import NonRetryableError

MAX_RETRY = 3
KEY_PREFIX = "ai:task:"
TASK_TTL_SECONDS = 60 * 60 * 24

logger = logging.getLogger(__name__)


class TaskManager:

    def __init__(self, redis: Redis, settings: Settings, services: dict[str, Any] | None = None) -> None:
        self.redis = redis
        self.settings = settings
        self._services = services
        self._services_loaded = services is not None
        self._workers: set[asyncio.Task[None]] = set()
        self._closing = False
        self._callback_hmac_key_warned = False
        # 回调 URL SSRF 守卫：建单 fail-fast + 执行回调前重检
        self._callback_guard = CallbackUrlGuard(settings)

    # ---------- 对外 API ----------

    async def create_task(self, request: TaskCreateRequest, request_id: str | None = None) -> TaskStatusResponse:
        if request.biz_type not in self._service_names():
            raise HTTPException(status_code=400, detail=f"unsupported biz_type: {request.biz_type}")
        # 回调 SSRF 防护：非法回调地址（非回环/不在白名单/含凭据等）建单即 400，失败快速
        if request.callback_url:
            try:
                await asyncio.to_thread(self._callback_guard.validate, request.callback_url)
            except ValueError as exc:
                raise HTTPException(status_code=400, detail=f"非法回调地址: {exc}") from exc
        # 超时治理——对 request.timeout 做上限裁剪。工作协程数量有限
        # （worker_count），客户端提交任意大的 timeout 会长时间占用一个协程、
        # 拖慢整条队列，且租约（= 超时 + 宽限）随超时等比放大、形同虚设。
        # 超上限值在此 clamp 到 max_timeout_seconds，保证任何任务最长执行可控。
        timeout = request.timeout or self.settings.default_timeout_seconds
        if timeout > self.settings.max_timeout_seconds:
            logger.warning(
                "task %s timeout %gs exceeds cap %gs, clamped",
                request.task_no, timeout, self.settings.max_timeout_seconds,
            )
            timeout = float(self.settings.max_timeout_seconds)
        now = _now()
        data = {
            "task_no": request.task_no,
            "biz_type": request.biz_type,
            "status": "QUEUED",
            "params": request.params,
            "callback_url": request.callback_url,
            "request_id": request_id,
            "result": None,
            "error": None,
            "retry_count": 0,
            "max_retry": self.settings.task_max_retry,
            "priority": request.priority,
            "timeout": timeout,
            "created_at": now,
            "updated_at": now,
        }
        # 去重原子化——SET NX 单命令判定「已存在」并落库，杜绝 check-then-set 竞态
        # （并发同 task_no 提交时，两个请求原本都经 _get 判空后各自覆盖写入产生重复任务，
        #  调度器多副本触发同名调度即命中此竞态）。NX 未写入说明同名任务已存在（含终态）→ 409。
        written = await self.redis.set(
            KEY_PREFIX + request.task_no, json.dumps(data, ensure_ascii=False), nx=True
        )
        if not written:
            raise HTTPException(status_code=409, detail=f"task already exists: {request.task_no}")
        try:
            await self._enqueue_ready(request.task_no, request.priority)
        except Exception:
            # 补偿删除：入队失败时清除刚落库的任务，避免「QUEUED 但不在任何队列」的
            # 孤儿（create_task 落库+入队两命令间崩溃的窗口由 recover_stale_tasks 兜底）
            await self.redis.delete(KEY_PREFIX + request.task_no)
            raise
        self._ensure_workers()
        ai_task_created_total.inc()
        return TaskStatusResponse(**data)

    async def get_task(self, task_no: str) -> TaskStatusResponse | None:
        data = await self._get(task_no)
        if data is None:
            return None
        return TaskStatusResponse(**{k: v for k, v in data.items() if k in TaskStatusResponse.model_fields})

    async def retry(self, task_no: str) -> TaskStatusResponse:
        data = await self._get(task_no)
        if data is None:
            raise HTTPException(status_code=404, detail="task not found")
        prev = json.dumps(data, ensure_ascii=False)
        data["status"] = "QUEUED"
        data["error"] = None
        # 手动重试重置失败分类，防止上一次失败的分类透传到后续回调
        data["reason"] = None
        data["updated_at"] = _now()
        await self._save(task_no, data)
        try:
            await self._enqueue_ready(task_no, int(data.get("priority", 5)))
        except Exception:
            # 回滚到入队前的原状态（终态），不把任务留在「QUEUED 但不在队列」的中间态
            await self.redis.set(KEY_PREFIX + task_no, prev)
            raise
        self._ensure_workers()
        return TaskStatusResponse(**{k: v for k, v in data.items() if k in TaskStatusResponse.model_fields})

    # ---------- 队列与工作线程 ----------

    def _ensure_workers(self) -> None:
        if self._workers or self._closing:
            return
        for _ in range(max(self.settings.worker_count, 1)):
            task = asyncio.create_task(self._worker_loop())
            self._workers.add(task)
            task.add_done_callback(self._on_worker_done)
        ai_worker_count.set(len(self._workers))

    def ensure_workers(self) -> None:
        """确保工作线程在运行（幂等）。进程重启后队列可能残留未消费任务，若之后无人
        提交新任务、_ensure_workers 不会被被动触发，遗留任务（QUEUED/delayed）将永久
        停滞——应用启动时须调用本方法主动拉起消费者。"""
        self._ensure_workers()

    def _on_worker_done(self, task: asyncio.Task[None]) -> None:
        self._workers.discard(task)
        if not self._closing:
            ai_worker_count.set(len(self._workers))

    async def close(self) -> None:
        """优雅停机：停止拉取并取消工作线程（运行中的任务被取消并记录为失败）。

        取消前先把在途 RUNNING 任务标记为 requeue——原实现直接
        cancel，在途任务保持 RUNNING、租约仍在未来，新 Pod 启动扫描只回收已过期租约，
        且宽限过后仍无周期回收，滚动发布后任务可卡死近 1 小时。现在停机即回收，
        新实例启动后立即可重跑。
        """
        self._closing = True
        for task in list(self._workers):
            task.cancel()
        if self._workers:
            await asyncio.gather(*list(self._workers), return_exceptions=True)
        self._workers.clear()
        ai_worker_count.set(0)
        # 回收在途任务：cancel 后 RUNNING 任务重新入队，避免跨实例重启期间永久停滞
        await self.recover_stale_tasks(force=True)

    async def _worker_loop(self) -> None:
        """消费循环：瞬时错误指数退避后继续，不因单个异常杀死消费线程。

        原实现中 `_execute`/`_promote_due`/`zpopmin` 任一处抛出（如 Redis 瞬时
        故障）都会让 while 循环退出、worker 协程消亡且不再重建（_ensure_workers
        仅在建单/手动重试时触发），整个消费端静默停机、队列永久停滞。
        此处把整轮循环包在 try 里：错误只触发退避重试；执行期错误把可能遗留
        RUNNING 的任务恢复为 QUEUED 重新入队；优雅停机/协程取消仍立即响应。
        """
        ready_key = self.settings.queue_ready_key
        delayed_key = self.settings.queue_delayed_key
        failure_backoff = 0.0
        empty_backoff = 0.0
        last_recovery = time.monotonic()
        while not self._closing:
            # 工作协程由首个提交任务的请求上下文派生（create_task 复制上下文），
            # 逐轮清空 request_id，避免无关任务日志错误携带建队请求的 ID
            request_id_var.set("")
            task_no: str | None = None
            try:
                # 周期租约回收——原实现仅在启动时扫描一次，
                # 滚动发布/重启后租约过期的 RUNNING 任务会卡死直到下次 Pod 重启；
                # 这里每 60s 由任一 worker 触发一次全局扫描（scan 幂等，多实例安全）
                if time.monotonic() - last_recovery >= 60.0:
                    await self.recover_stale_tasks()
                    last_recovery = time.monotonic()
                await self._promote_due(delayed_key, ready_key)
                popped = await self.redis.zpopmin(ready_key)
                if not popped:
                    failure_backoff = 0.0
                    # 空队列退避指数化（0.02→0.05→0.1→…封顶 0.5s），
                    # 原固定 20ms 忙轮询在扩副本后每秒产生大量无谓 Redis 命令
                    empty_backoff = min(empty_backoff * 2 + 0.02, 0.5)
                    await asyncio.sleep(empty_backoff)
                    continue
                empty_backoff = 0.0
                task_no = popped[0][0]
                await self._execute(task_no)
                failure_backoff = 0.0
            except asyncio.CancelledError:
                raise
            except Exception as exception:  # noqa: BLE001 —— 瞬时错误不杀死消费线程
                failure_backoff = min(failure_backoff * 2 + 0.5, 10.0)
                if task_no is not None:
                    await self._requeue_after_error(task_no, exception)
                logger.warning(
                    "AI worker transient error, backoff %.1fs: %s",
                    failure_backoff, exception,
                )
                await asyncio.sleep(failure_backoff)

    async def _promote_due(self, delayed_key: str, ready_key: str) -> None:
        now = time.time()
        due = await self.redis.zrangebyscore(delayed_key, 0, now)
        if not due:
            return
        # 检查 zrem 返回值——多实例同时读到同一批 due 时，
        # 仅实际删除（>0）的实例拥有处置权，另一实例跳过，语义与 run_loop 对齐
        removed = await self.redis.zrem(delayed_key, *due)
        if not removed:
            return
        for task_no in due:
            data = await self._get(task_no)
            if data is None:
                continue
            await self._enqueue_ready(task_no, int(data.get("priority", 5)))

    async def _enqueue_ready(self, task_no: str, priority: int) -> None:
        # score 取负：优先级越高（数值越大）score 越小，zpopmin 先弹出
        await self.redis.zadd(self.settings.queue_ready_key, {task_no: -int(priority)})

    async def _execute(self, task_no: str) -> None:
        data = await self._get(task_no)
        if data is None or data.get("status") == "RUNNING":
            return
        # 执行期间日志携带提交该任务的请求 ID，跨服务排障可串联调用方
        request_id_var.set(data.get("request_id") or "")
        # 执行侧同样做上限裁剪——兼容修复前已入库的旧任务（未 clamp 的超大
        # timeout 可能已在 Redis 中），保证任何任务执行与租约都受 max_timeout 约束
        timeout = min(
            float(data.get("timeout") or self.settings.default_timeout_seconds),
            float(self.settings.max_timeout_seconds),
        )
        data["status"] = "RUNNING"
        data["started_at"] = _now()
        # 崩溃自愈租约：执行受 asyncio.wait_for 超时约束，租约 = 超时 + 宽限期，
        # 存活的执行必在租约到期前离开 RUNNING；崩溃遗留的任务由启动扫描按过期
        # 租约回收重新入队（recover_stale_tasks），避免永久卡死 RUNNING
        data["lease_until"] = time.time() + timeout + self.settings.stale_task_lease_grace_seconds
        data["updated_at"] = _now()
        await self._save(task_no, data)
        try:
            service = self._get_service(data["biz_type"])
            result = await asyncio.wait_for(service.run(data["params"]), timeout=timeout)
        except TimeoutError:
            data["error"] = f"task timeout after {timeout:g}s"
            data["retry_count"] = data.get("retry_count", 0) + 1
            await self._fail(task_no, data, reason="timeout")
            return
        except NonRetryableError as exception:
            data["error"] = str(exception)
            data["retry_count"] = data.get("retry_count", 0) + 1
            await self._fail(task_no, data, reason="non_retryable")
            return
        except Exception as exception:  # noqa: BLE001 —— 其余异常按可重试处理
            data["error"] = str(exception)
            data["retry_count"] = data.get("retry_count", 0) + 1
            data["updated_at"] = _now()
            if data["retry_count"] < data.get("max_retry", MAX_RETRY):
                data["status"] = "QUEUED"
                await self._save(task_no, data)
                await self._schedule_retry(task_no, data)
            else:
                await self._fail(task_no, data, reason="retries_exhausted")
            return

        data["status"] = "SUCCEEDED"
        data["result"] = result
        data["error"] = None
        # 重试后成功的任务清除历史失败分类，避免陈旧 reason 透传到成功回调
        data["reason"] = None
        data["updated_at"] = _now()
        ai_task_succeeded_total.inc()
        await self._save(task_no, data)
        await self._expire(task_no)
        await self._callback(task_no, data)

    async def _schedule_retry(self, task_no: str, data: dict[str, Any]) -> None:
        ai_task_retried_total.inc()
        # 指数退避 + 抖动——原固定 0.5s 重入队，LLM provider 限流时
        # 3 次重试在 1s 内打完直接加剧上游过载；现按 retry_count 指数退避（0.5*2^n，
        # 封顶 60s）+ 随机 ±20% 抖动防惊群
        import random

        retry_count = int(data.get("retry_count", 1))
        backoff = min(float(self.settings.retry_backoff_seconds) * (2 ** (retry_count - 1)), 60.0)
        jitter = backoff * 0.2 * (random.random() * 2 - 1)
        await self.redis.zadd(self.settings.queue_delayed_key, {task_no: time.time() + backoff + jitter})

    async def _fail(self, task_no: str, data: dict[str, Any], reason: str = "unknown") -> None:
        data["status"] = "FAILED"
        # 失败分类（timeout / non_retryable / retries_exhausted）随任务记录持久化，
        # 供回调契约透传与 GET /tasks 读取。此前分类仅进死信与指标标签，后端系统记录无从得知
        data["reason"] = reason
        data["updated_at"] = _now()
        # reason 见 app.core.metrics 模块 docstring：timeout / non_retryable / retries_exhausted
        ai_task_failed_total.labels(reason=reason).inc()
        await self._record_dead_letter(task_no, data, reason)
        await self._save(task_no, data)
        await self._expire(task_no)
        await self._callback(task_no, data)

    async def _record_dead_letter(self, task_no: str, data: dict[str, Any], reason: str) -> None:
        """写死信并裁剪队列长度上限（见 queue_dead_max_len）。

        死信列表纯写入、无消费者，若只 rpush 不裁剪则随失败总数无界增长。
        rpush + ltrim 用事务（MULTI/EXEC）包住：两条命令原子生效，并发失败时
        列表不会出现中间态超长。仅保留最近 queue_dead_max_len 条，0 表示不裁剪。
        记录带 reason（与 ai_task_failed_total 标签一致）以及富化的
        biz_type / retry_count / failed_at，供运维按时间/类型离线排查失败原因，
        并经 GET /api/v1/tasks/dead 查询、DELETE /api/v1/tasks/dead 清理。
        """
        payload = json.dumps(
            {
                "task_no": task_no,
                "biz_type": data.get("biz_type"),
                "error": data.get("error"),
                "reason": reason,
                "retry_count": data.get("retry_count", 0),
                "failed_at": _now(),
            },
            ensure_ascii=False,
        )
        max_len = self.settings.queue_dead_max_len
        async with self.redis.pipeline(transaction=True) as pipe:
            await pipe.rpush(self.settings.queue_dead_key, payload)
            if max_len > 0:
                await pipe.ltrim(self.settings.queue_dead_key, -max_len, -1)
            await pipe.execute()

    async def list_dead_letters(self, limit: int = 100) -> list[DeadLetterEntry]:
        """列出死信队列（新失败在前），供 GET /api/v1/tasks/dead 透出。

        列表尾部（rpush 写入 + ltrim 裁剪保留的最近区段）即最新失败：先
        lrange 取尾部 limit 条再反转。返回 DeadLetterEntry，字段与写入结构
        一致（含 failed_at / biz_type / retry_count），兼容修复前旧记录。
        """
        if limit <= 0:
            return []
        raw = await self.redis.lrange(self.settings.queue_dead_key, -limit, -1)
        entries = [json.loads(item) for item in raw]
        entries.reverse()
        return [DeadLetterEntry(**entry) for entry in entries]

    async def purge_dead_letters(self) -> int:
        """清空死信队列并返回清理条数，供 DELETE /api/v1/tasks/dead 运维收尾。"""
        size = await self.redis.llen(self.settings.queue_dead_key)
        if size > 0:
            await self.redis.delete(self.settings.queue_dead_key)
        return size

    async def _requeue_after_error(self, task_no: str, error: Exception) -> None:
        """执行期异常兜底：任务仍非终态时恢复为 QUEUED 重新入队。

        `_execute` 内部已把 service.run 的异常分流到重试/死信，逃逸到这里的异常
        来自执行中的 Redis 写入、回调等瞬时故障。此时任务可能已标记 RUNNING（
        RUNNING 落库成功、后续步骤失败）或仍为 QUEUED（RUNNING 落库本身失败，
        任务已被 zpopmin 弹出、不在任何队列）。两者均恢复为 QUEUED 重新入队，
        交由后续轮次重试。恢复本身失败则静默，交给启动自愈兜底。
        """
        try:
            data = await self._get(task_no)
            if data is None or data.get("status") not in ("QUEUED", "RUNNING"):
                return
            data["status"] = "QUEUED"
            data["error"] = f"requeued after worker transient error: {error}"
            data["updated_at"] = _now()
            await self._save(task_no, data)
            await self._enqueue_ready(task_no, int(data.get("priority", 5)))
        except Exception as exception:  # noqa: BLE001 —— 恢复失败不再抛出，交启动自愈兜底
            logger.warning(
                "AI worker failed to requeue task %s after transient error: %s",
                task_no, exception,
            )

    async def recover_stale_tasks(self, force: bool = False) -> int:
        """启动自愈：回收两类遗留任务为 QUEUED 重新入队。

        1. 租约已过期的 RUNNING：工作线程崩溃 / 进程被杀（OOM、滚动发布、k8s 重启）
           会让任务永久停留在 RUNNING：_execute 遇 RUNNING 直接跳过、无任何恢复
           路径，任务直至 24h TTL 才消失，期间既不重试也不回调，等于任务永久丢失。
           以租约判定归属：执行受 asyncio.wait_for 超时约束，存活的执行必在
           lease_until（= 超时 + 宽限）前离开 RUNNING，故租约过期是确凿的崩溃遗留，
           多实例下也不会误回收其它实例正在执行的任务。
        2. 「QUEUED 但不在任何队列」的孤儿：create_task 落库与入队之间崩溃 /
           入队补偿删除失败等窗口会留下 status=QUEUED 却不在 ready/delayed 的
           记录，任何 worker 都无法消费。孤儿判定：zscore 查 ready 与 delayed
           均不存在。非 force 时要求记录足够旧（updated_at 距今超过孤儿宽限），
           避免误伤刚被 zpopmin 认领、尚未写 RUNNING 的在途任务。

        force=True：忽略租约/时间戳直接回收——仅用于本实例优雅停机
        （close 已取消全部 worker，在途 RUNNING/QUEUED 均属本实例且不会再推进）。
        返回回收数量。
        """
        now = time.time()
        recovered = 0
        cursor: int | str = 0
        while True:
            cursor, keys = await self.redis.scan(cursor, match=KEY_PREFIX + "*", count=200)
            for key in keys:
                task_no = key.removeprefix(KEY_PREFIX)
                data = await self._get(task_no)
                if data is None:
                    continue
                status = data.get("status")
                if status == "RUNNING":
                    if not force:
                        lease_until = data.get("lease_until")
                        if lease_until is not None and float(lease_until) > now:
                            continue  # 租约未过期：可能仍被其它实例执行
                elif status == "QUEUED":
                    # 孤儿判定：不在 ready 也不在 delayed 才回收
                    in_ready = await self.redis.zscore(self.settings.queue_ready_key, task_no)
                    in_delayed = await self.redis.zscore(self.settings.queue_delayed_key, task_no)
                    if in_ready is not None or in_delayed is not None:
                        continue
                    if not force and not self._is_orphan_stale(data, now):
                        continue  # 可能刚被认领在途，等下一轮再判定
                else:
                    continue  # 终态/其它状态不回收
                data["status"] = "QUEUED"
                data["error"] = "recovered after worker interruption"
                data["updated_at"] = _now()
                await self._save(task_no, data)
                await self._enqueue_ready(task_no, int(data.get("priority", 5)))
                recovered += 1
            if not cursor:
                break
        if recovered:
            logger.warning("AI task recovery: requeued %s stale task(s)", recovered)
        return recovered

    @staticmethod
    def _is_orphan_stale(data: dict[str, Any], now: float) -> bool:
        """孤儿宽限判定：updated_at 距今超过 stale_task_lease_grace_seconds 才算遗留。

        无法解析时间戳的旧记录（本特性上线前遗留）按遗留处理。
        """
        raw = data.get("updated_at")
        if not raw:
            return True
        try:
            updated = datetime.fromisoformat(str(raw)).timestamp()
        except (TypeError, ValueError):
            return True
        return now - updated > TaskManager._stale_grace_seconds

    _stale_grace_seconds = 10.0  # 独立于租约宽限的孤儿时间戳宽限（秒）

    # ---------- 服务解析 ----------

    def _service_names(self) -> list[str]:
        services = self._services if self._services_loaded else self._load_services()
        return list(services)

    def _get_service(self, biz_type: str) -> Any:
        services = self._services if self._services_loaded else self._load_services()
        service = services.get(biz_type)
        if service is None:
            raise NonRetryableError(f"unsupported biz_type: {biz_type}")
        return service

    def _load_services(self) -> dict[str, Any]:
        """惰性装配默认服务上下文：未显式注入 services 时，用自身 redis/settings 构建
        （Mock 提供方 + Redis 向量存储），保证测试与单实例场景开箱即用。"""
        from app.llm import build_registry
        from app.services import ServiceContext, build_services
        from app.vectors import RedisVectorStore

        context = ServiceContext(
            redis=self.redis,
            settings=self.settings,
            providers=build_registry(self.settings),
            vectors=RedisVectorStore(self.redis, self.settings.vector_namespace_prefix),
            scheduler=None,
        )
        self._services = build_services(context)
        self._services_loaded = True
        return self._services

    # ---------- 回调（HMAC 签名，字节级与后端 AiTaskService 对齐） ----------

    async def _callback(self, task_no: str, data: dict[str, Any]) -> None:
        callback_url = data.get("callback_url")
        if not callback_url:
            return
        # 防御纵深：执行回调前重检回调地址（兼容修复前已入库的任务 / 越权写入的地址），
        # 不合法则跳过并告警——回调失败不应反过来影响任务终态判定
        if not await asyncio.to_thread(self._callback_guard.is_safe, callback_url):
            logger.warning(
                "AI callback for %s skipped: 回调地址不在允许范围（未配置白名单时仅回环）: %s",
                task_no, callback_url,
            )
            return
        payload = {
            "task_no": task_no,
            "biz_type": data.get("biz_type"),
            "status": data.get("status"),
            "result": data.get("result"),
            "error": data.get("error"),
            "retry_count": data.get("retry_count", 0),
            # 失败分类（timeout / non_retryable / retries_exhausted）随回调透传，
            # 后端落 ai_task.error_type；成功路径已清空，成功回调为 null
            "reason": data.get("reason"),
        }
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        # 出站回调签名密钥：优先 CALLBACK_HMAC_KEY；未配置时回退 AUTH_TOKEN（与后端
        # 现状一致——Java 侧 validCallbackHmac 用 AiServiceConfig.apiKey 验签，而该
        # apiKey 同时是入站 Bearer 密钥，故拆分密钥前必须回退以免破坏跨端契约）。
        # 共用密钥的隐患（任一侧泄漏危及另一侧）由时间戳防重放 + 回调 SSRF 白名单
        # 缓解；后端拆分独立验签密钥后注入 CALLBACK_HMAC_KEY 即可平滑轮换。
        signing_key = self.settings.callback_hmac_key or self.settings.auth_token
        if not self.settings.callback_hmac_key and not self._callback_hmac_key_warned:
            self._callback_hmac_key_warned = True
            logger.warning(
                "未配置 CALLBACK_HMAC_KEY，出站回调沿用 AUTH_TOKEN 签名（后端当前共用同一 "
                "密钥验签，属兼容回退；生产拆分验签密钥后请注入新键）"
            )
        timestamp = str(int(time.time()))
        signature = hmac.new(
            signing_key.encode("utf-8"),
            timestamp.encode("utf-8") + b"\n" + body,
            hashlib.sha256,
        ).hexdigest()
        headers = {
            "X-Ai-Timestamp": timestamp,
            "X-Ai-Signature": signature,
        }
        if data.get("request_id"):
            headers["X-Request-Id"] = data["request_id"]
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                # follow_redirects=False：禁止回调被重定向到未校验的地址（SSRF 纵深）
                async with httpx.AsyncClient(timeout=5, trust_env=False, follow_redirects=False) as client:
                    response = await client.post(callback_url, json=payload, headers=headers)
                    response.raise_for_status()
                logger.info("AI callback for %s returned status %s", task_no, response.status_code)
                return
            except httpx.HTTPError as exception:
                last_error = exception
                logger.warning("AI callback for %s failed attempt %s: %s", task_no, attempt + 1, exception)
                await asyncio.sleep(1 + attempt)
        logger.error("AI callback for %s failed after retries: %s", task_no, last_error)

    # ---------- 存储 ----------

    async def _save(self, task_no: str, data: dict[str, Any]) -> None:
        await self.redis.set(KEY_PREFIX + task_no, json.dumps(data, ensure_ascii=False))

    async def _get(self, task_no: str) -> dict[str, Any] | None:
        raw = await self.redis.get(KEY_PREFIX + task_no)
        if raw is None:
            return None
        return json.loads(raw)

    async def _expire(self, task_no: str) -> None:
        await self.redis.expire(KEY_PREFIX + task_no, TASK_TTL_SECONDS)


def _now() -> str:
    return datetime.now(UTC).isoformat()
