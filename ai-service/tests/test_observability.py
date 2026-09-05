"""跨服务 request_id 贯穿 + 统一结构化日志。

覆盖：
- 中间件：回显调用方 X-Request-Id / 无则生成、响应回写、一行访问日志
  （方法/路径/状态/耗时/客户端/request_id）；
- 日志格式器：业务日志自动携带当前请求 ID，无请求归属回退 '-'；
- 任务执行：worker 执行期间 request_id_var 携带提交该任务的请求 ID。
"""

import logging

import fakeredis.aioredis
import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.core.config import settings
from app.core.observability import RequestIdFormatter, request_id_var
from app.schemas.task import TaskCreateRequest
from app.tasks.manager import TaskManager

AUTH = {"Authorization": f"Bearer {settings.auth_token}"}

# 让 lifespan 里的 Redis.from_url(...) 返回 fakeredis，避免依赖真实 Redis
main_module.Redis = fakeredis.aioredis.FakeRedis


def _boot_client():
    return TestClient(main_module.app)


# ---------- 中间件：request_id 贯穿与访问日志 ----------


def test_incoming_request_id_is_echoed() -> None:
    with _boot_client() as client:
        response = client.get("/health", headers={"X-Request-Id": "incoming-req-1"})
    assert response.status_code == 200
    assert response.headers.get("X-Request-Id") == "incoming-req-1"


def test_request_id_generated_when_absent() -> None:
    with _boot_client() as client:
        response = client.get("/health")
    request_id = response.headers.get("X-Request-Id")
    assert request_id is not None
    assert len(request_id) == 32  # uuid4().hex


def test_access_log_records_request_with_request_id(caplog) -> None:
    caplog.set_level(logging.INFO, logger="app.core.observability")
    with _boot_client() as client:
        response = client.get("/health", headers={"X-Request-Id": "acc-req-1"})
    records = [r for r in caplog.records if r.name == "app.core.observability"]
    assert response.status_code == 200
    assert any(
        getattr(r, "request_id", None) == "acc-req-1"
        and "method=GET" in r.getMessage()
        and "path=/health" in r.getMessage()
        and "status=200" in r.getMessage()
        for r in records
    ), [r.getMessage() for r in records]


# ---------- 格式器：业务日志请求 ID 注入 ----------


def _record(message: str = "hello") -> logging.LogRecord:
    return logging.LogRecord("app.test", logging.INFO, "test.py", 1, message, (), None)


def test_formatter_defaults_to_dash_when_no_request() -> None:
    formatter = RequestIdFormatter("%(request_id)s %(message)s")
    assert formatter.format(_record()) == "- hello"


def test_formatter_carries_current_request_id() -> None:
    token = request_id_var.set("ctx-req-1")
    try:
        formatter = RequestIdFormatter("%(request_id)s %(message)s")
        assert formatter.format(_record()) == "ctx-req-1 hello"
    finally:
        request_id_var.reset(token)


def test_formatter_keeps_explicit_extra_request_id() -> None:
    record = _record()
    record.request_id = "extra-req-1"  # 访问日志经 extra 显式传入
    formatter = RequestIdFormatter("%(request_id)s %(message)s")
    assert formatter.format(record) == "extra-req-1 hello"


# ---------- 任务执行：日志关联提交任务的请求 ID ----------


@pytest.mark.asyncio
async def test_task_execution_carries_submitting_request_id() -> None:
    seen: dict[str, str] = {}

    class _ProbeService:
        async def run(self, params: dict) -> dict:
            seen["request_id"] = request_id_var.get()
            return {"ok": True}

    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, settings, services={"job": _ProbeService()})
    manager._ensure_workers = lambda: None  # 不启动后台 worker，直接驱动 _execute
    await manager.create_task(
        TaskCreateRequest(task_no="obs-1", biz_type="job", params={}),
        request_id="from-req-1",
    )

    await manager._execute("obs-1")

    assert seen["request_id"] == "from-req-1"
