import asyncio
from types import SimpleNamespace
from unittest.mock import patch

import fakeredis.aioredis
import pytest
from fastapi import HTTPException

from app.core.config import Settings
from app.schemas.task import TaskCreateRequest
from app.tasks.manager import TaskManager


@pytest.mark.asyncio
async def test_task_success_flow() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())
    task = await manager.create_task(TaskCreateRequest(
        task_no="task-success",
        biz_type="text_summary",
        params={"content": "这是一段用于测试摘要的文本。第二句。第三句。"},
    ))
    assert task.status == "QUEUED"

    current = await _wait_for_terminal(manager, "task-success")
    assert current is not None
    assert current.status == "SUCCEEDED"
    assert current.result["summary"]


@pytest.mark.asyncio
async def test_task_failure_retries_then_failed() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())
    await manager.create_task(TaskCreateRequest(
        task_no="task-fail",
        biz_type="keyword_extract",
        params={"content": "测试", "force_fail": True},
    ))

    current = await _wait_for_terminal(manager, "task-fail", attempts=40)
    assert current is not None
    assert current.status == "FAILED"
    assert current.retry_count == 3


@pytest.mark.asyncio
async def test_unknown_biz_type_rejected() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())
    with pytest.raises(HTTPException):
        await manager.create_task(TaskCreateRequest(
            task_no="task-bad",
            biz_type="not_exist",
            params={},
        ))


@pytest.mark.asyncio
async def test_callback_sends_service_token() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(callback_token="secret-token"))
    captured = {}

    class FakeAsyncClient:
        def __init__(self, **kwargs):
            self.kwargs = kwargs

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def post(self, url, json=None, headers=None):
            captured["url"] = url
            captured["headers"] = headers
            captured["json"] = json
            response = SimpleNamespace(status_code=200)
            response.raise_for_status = lambda: None
            return response

    with patch("app.tasks.manager.httpx.AsyncClient", FakeAsyncClient):
        await manager._callback("task-callback", {
            "callback_url": "http://localhost:8080/api/ai/callback/task",
            "biz_type": "text_summary",
            "status": "SUCCEEDED",
            "result": {"summary": "ok"},
            "error": None,
        })

    assert captured["headers"]["X-Ai-Service-Token"] == "secret-token"
    assert captured["url"] == "http://localhost:8080/api/ai/callback/task"


async def _wait_for_terminal(manager: TaskManager, task_no: str, attempts: int = 20):
    current = None
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        current = await manager.get_task(task_no)
        if current is not None and current.status in ("SUCCEEDED", "FAILED"):
            return current
    return current
