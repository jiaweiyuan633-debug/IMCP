import asyncio
import hashlib
import hmac
import json
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

    # 重试带 0.5s 退避（3 次退避 ≈ 1.5s），放大轮询窗口
    current = await _wait_for_terminal(manager, "task-fail", attempts=200)
    assert current is not None
    assert current.status == "FAILED"
    assert current.retry_count == 3
    # R4-1.20：重试耗尽分类必须随任务记录保留，可经 GET /tasks 读取
    assert current.reason == "retries_exhausted"


@pytest.mark.asyncio
async def test_dead_letter_list_and_purge() -> None:
    """R4-1.21：死信记录富化失败时间戳/任务类型/重试次数，可经 list/purge 管理。

    修复前死信记录只有 task_no/error/reason——无失败时刻、无任务类型，运维无法
    按时间排障；且队列只写无读/无清，历史失败只能被新失败挤出裁剪窗口。此断言
    覆盖：真实失败写入富化死信（failed_at/biz_type/retry_count 齐备）、
    list_dead_letters 新失败在前、purge_dead_letters 全量清空。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())
    await manager.create_task(TaskCreateRequest(
        task_no="task-dl",
        biz_type="keyword_extract",
        params={"content": "测试", "force_fail": True},
    ))

    current = await _wait_for_terminal(manager, "task-dl", attempts=200)
    assert current is not None and current.status == "FAILED"

    entries = await manager.list_dead_letters()
    assert len(entries) >= 1
    latest = entries[0]
    assert latest.task_no == "task-dl"
    assert latest.reason == "retries_exhausted"
    assert latest.biz_type == "keyword_extract"
    assert latest.retry_count == 3
    assert latest.failed_at is not None

    purged = await manager.purge_dead_letters()
    assert purged >= 1
    assert await manager.list_dead_letters() == []


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
async def test_callback_signs_with_hmac() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(auth_token="secret-token"))
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

    assert captured["url"] == "http://localhost:8080/api/ai/callback/task"
    # 不再透发明文 token，改为 HMAC 签名 + 时间戳（防伪造/重放）
    assert "X-Ai-Service-Token" not in captured["headers"]
    timestamp = captured["headers"]["X-Ai-Timestamp"]
    signature = captured["headers"]["X-Ai-Signature"]
    body = json.dumps(captured["json"], ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    expected = hmac.new(
        b"secret-token",
        timestamp.encode("utf-8") + b"\n" + body,
        hashlib.sha256,
    ).hexdigest()
    assert hmac.compare_digest(signature, expected)


@pytest.mark.asyncio
async def test_callback_carries_failure_reason_for_failed_task() -> None:
    """R4-1.20：失败分类（reason）必须随回调载荷透传，后端据此落 ai_task.error_type。

    若回退到修复前契约（回调只带 error 文本、不带分类），后端系统记录无法区分
    瞬时超时（timeout，值得重试）与确定性错误（non_retryable，重试无意义）。
    """
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(auth_token="secret-token"))
    captured = {}

    class FakeAsyncClient:
        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def post(self, url, json=None, headers=None):
            captured["json"] = json
            response = SimpleNamespace(status_code=200)
            response.raise_for_status = lambda: None
            return response

    with patch("app.tasks.manager.httpx.AsyncClient", FakeAsyncClient):
        await manager._callback("task-dead", {
            "callback_url": "http://127.0.0.1:8080/api/ai/callback/task",
            "biz_type": "text_summary",
            "status": "FAILED",
            "result": None,
            "error": "task timeout after 60s",
            "reason": "timeout",
        })

    assert captured["json"]["status"] == "FAILED"
    assert captured["json"]["reason"] == "timeout"
    assert captured["json"]["error"] == "task timeout after 60s"


async def _wait_for_terminal(manager: TaskManager, task_no: str, attempts: int = 20):
    current = None
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        current = await manager.get_task(task_no)
        if current is not None and current.status in ("SUCCEEDED", "FAILED"):
            return current
    return current
