import asyncio
import json
import logging
from datetime import UTC, datetime
from typing import Any

import httpx
from fastapi import HTTPException
from redis.asyncio import Redis

from app.core.config import Settings
from app.schemas.task import TaskCreateRequest, TaskStatusResponse
from app.services import SERVICE_REGISTRY, get_service

MAX_RETRY = 3
KEY_PREFIX = "ai:task:"
TASK_TTL_SECONDS = 60 * 60 * 24

logger = logging.getLogger(__name__)


class TaskManager:

    def __init__(self, redis: Redis, settings: Settings) -> None:
        self.redis = redis
        self.settings = settings

    async def create_task(self, request: TaskCreateRequest, request_id: str | None = None) -> TaskStatusResponse:
        if request.biz_type not in SERVICE_REGISTRY:
            raise HTTPException(status_code=400, detail=f"unsupported biz_type: {request.biz_type}")

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
            "max_retry": MAX_RETRY,
            "created_at": now,
            "updated_at": now,
        }
        await self._save(request.task_no, data)
        asyncio.create_task(self._execute(request.task_no))
        return TaskStatusResponse(**data)

    async def get_task(self, task_no: str) -> TaskStatusResponse | None:
        data = await self._get(task_no)
        if data is None:
            return None
        return TaskStatusResponse(**data)

    async def retry(self, task_no: str) -> TaskStatusResponse:
        data = await self._get(task_no)
        if data is None:
            raise HTTPException(status_code=404, detail="task not found")
        data["status"] = "QUEUED"
        data["error"] = None
        data["updated_at"] = _now()
        await self._save(task_no, data)
        asyncio.create_task(self._execute(task_no))
        return TaskStatusResponse(**data)

    async def _execute(self, task_no: str) -> None:
        data = await self._get(task_no)
        if data is None:
            return

        data["status"] = "RUNNING"
        data["updated_at"] = _now()
        await self._save(task_no, data)

        try:
            service = get_service(data["biz_type"])
            result = await service.run(data["params"])
            data["status"] = "SUCCEEDED"
            data["result"] = result
            data["error"] = None
            data["updated_at"] = _now()
            await self._save(task_no, data)
            await self._expire(task_no)
            await self._callback(task_no, data)
        except Exception as exception:  # noqa: BLE001
            data["error"] = str(exception)
            data["retry_count"] += 1
            data["updated_at"] = _now()
            if data["retry_count"] < data.get("max_retry", MAX_RETRY):
                data["status"] = "QUEUED"
                await self._save(task_no, data)
                await asyncio.sleep(0)
                asyncio.create_task(self._execute(task_no))
            else:
                data["status"] = "FAILED"
                await self._save(task_no, data)
                await self._expire(task_no)
                await self._callback(task_no, data)

    async def _callback(self, task_no: str, data: dict[str, Any]) -> None:
        callback_url = data.get("callback_url")
        if not callback_url:
            return
        payload = {
            "task_no": task_no,
            "biz_type": data.get("biz_type"),
            "status": data.get("status"),
            "result": data.get("result"),
            "error": data.get("error"),
            "retry_count": data.get("retry_count", 0),
        }
        headers = {"X-Ai-Service-Token": self.settings.callback_token}
        if data.get("request_id"):
            headers["X-Request-Id"] = data["request_id"]
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                async with httpx.AsyncClient(timeout=5, trust_env=False) as client:
                    response = await client.post(callback_url, json=payload, headers=headers)
                logger.info("AI callback for %s returned status %s", task_no, response.status_code)
                return
            except httpx.HTTPError as exception:
                last_error = exception
                logger.warning("AI callback for %s failed attempt %s: %s", task_no, attempt + 1, exception)
                await asyncio.sleep(1 + attempt)
        logger.error("AI callback for %s failed after retries: %s", task_no, last_error)

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
