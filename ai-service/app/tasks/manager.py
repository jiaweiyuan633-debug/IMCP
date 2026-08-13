"""任务队列深化：优先级 + 超时 + 可重试分类 + 去重 + 死信 + 有界工作池。

队列结构（Redis）：
- ``ai:queue:ready``     zset，score = -priority（优先级越大越先执行；同优先级按任务号字典序）
- ``ai:queue:delayed``   zset，score = 就绪时间戳（重试退避到期后由工作线程提升到 ready）
- ``ai:queue:dead``      list，超出重试或不可重试的失败任务（JSON 记录）

执行模型：固定数量工作线程（settings.worker_count）持续消费 ready 队列；
单任务执行受 ``asyncio.wait_for`` 超时保护；可重试异常按指数式/固定退避重新入队，
不可重试异常（NonRetryableError/超时）直接失败并写死信。
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
from app.core.observability import request_id_var
from app.schemas.task import TaskCreateRequest, TaskStatusResponse
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
        # 去重：同名任务已存在（含已结束）直接 409，避免重复提交
        if await self._get(request.task_no) is not None:
            raise HTTPException(status_code=409, detail=f"task already exists: {request.task_no}")

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
            "timeout": request.timeout or self.settings.default_timeout_seconds,
            "created_at": now,
            "updated_at": now,
        }
        await self._save(request.task_no, data)
        await self._enqueue_ready(request.task_no, request.priority)
        self._ensure_workers()
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
        data["status"] = "QUEUED"
        data["error"] = None
        data["updated_at"] = _now()
        await self._save(task_no, data)
        await self._enqueue_ready(task_no, int(data.get("priority", 5)))
        self._ensure_workers()
        return TaskStatusResponse(**{k: v for k, v in data.items() if k in TaskStatusResponse.model_fields})

    # ---------- 队列与工作线程 ----------

    def _ensure_workers(self) -> None:
        if self._workers or self._closing:
            return
        for _ in range(max(self.settings.worker_count, 1)):
            task = asyncio.create_task(self._worker_loop())
            self._workers.add(task)
            task.add_done_callback(self._workers.discard)

    async def close(self) -> None:
        """优雅停机：停止拉取并取消工作线程（运行中的任务被取消并记录为失败）。"""
        self._closing = True
        for task in list(self._workers):
            task.cancel()
        if self._workers:
            await asyncio.gather(*list(self._workers), return_exceptions=True)
        self._workers.clear()

    async def _worker_loop(self) -> None:
        ready_key = self.settings.queue_ready_key
        delayed_key = self.settings.queue_delayed_key
        while not self._closing:
            # 工作协程由首个提交任务的请求上下文派生（create_task 复制上下文），
            # 逐轮清空 request_id，避免无关任务日志错误携带建队请求的 ID
            request_id_var.set("")
            await self._promote_due(delayed_key, ready_key)
            popped = await self.redis.zpopmin(ready_key)
            if not popped:
                await asyncio.sleep(0.02)
                continue
            task_no = popped[0][0]
            await self._execute(task_no)

    async def _promote_due(self, delayed_key: str, ready_key: str) -> None:
        now = time.time()
        due = await self.redis.zrangebyscore(delayed_key, 0, now)
        if not due:
            return
        await self.redis.zrem(delayed_key, *due)
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
        data["status"] = "RUNNING"
        data["updated_at"] = _now()
        await self._save(task_no, data)

        timeout = float(data.get("timeout") or self.settings.default_timeout_seconds)
        try:
            service = self._get_service(data["biz_type"])
            result = await asyncio.wait_for(service.run(data["params"]), timeout=timeout)
        except TimeoutError:
            data["error"] = f"task timeout after {timeout:g}s"
            data["retry_count"] = data.get("retry_count", 0) + 1
            await self._fail(task_no, data)
            return
        except NonRetryableError as exception:
            data["error"] = str(exception)
            data["retry_count"] = data.get("retry_count", 0) + 1
            await self._fail(task_no, data)
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
                await self._fail(task_no, data)
            return

        data["status"] = "SUCCEEDED"
        data["result"] = result
        data["error"] = None
        data["updated_at"] = _now()
        await self._save(task_no, data)
        await self._expire(task_no)
        await self._callback(task_no, data)

    async def _schedule_retry(self, task_no: str, data: dict[str, Any]) -> None:
        backoff = float(self.settings.retry_backoff_seconds)
        await self.redis.zadd(self.settings.queue_delayed_key, {task_no: time.time() + backoff})

    async def _fail(self, task_no: str, data: dict[str, Any]) -> None:
        data["status"] = "FAILED"
        data["updated_at"] = _now()
        await self.redis.rpush(
            self.settings.queue_dead_key,
            json.dumps({"task_no": task_no, "error": data.get("error")}, ensure_ascii=False),
        )
        await self._save(task_no, data)
        await self._expire(task_no)
        await self._callback(task_no, data)

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
        }
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        timestamp = str(int(time.time()))
        signature = hmac.new(
            self.settings.auth_token.encode("utf-8"),
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
