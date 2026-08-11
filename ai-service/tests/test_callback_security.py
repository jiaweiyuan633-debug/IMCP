"""回调 URL SSRF 守卫测试（R1-1.3）：双模式放行/拒绝 + DNS 重绑定兜底 + 建单 fail-fast。"""

import socket
from unittest.mock import patch

import fakeredis.aioredis
import pytest
from fastapi import HTTPException

from app.core.callback_security import CallbackUrlGuard
from app.core.config import Settings
from app.schemas.task import TaskCreateRequest
from app.tasks.manager import TaskManager


def guard(origins: list[str] | None = None) -> CallbackUrlGuard:
    return CallbackUrlGuard(Settings(callback_allowed_origins=origins or []))


# ---------- 默认模式（未配置白名单）：仅回环 ----------

@pytest.mark.parametrize("url", [
    "http://localhost:8080/api/ai/callback/task",
    "http://127.0.0.1:8080/api/ai/callback/task",
    "https://localhost/api/ai/callback/task",
    "http://[::1]:8080/api/ai/callback/task",
])
def test_default_mode_allows_loopback(url: str) -> None:
    assert guard().is_safe(url)


@pytest.mark.parametrize("url", [
    "http://169.254.169.254/latest/meta-data/",  # 云元数据
    "http://169.254.1.1/x",                      # 链路本地
    "http://10.0.0.1/x",                         # 私网
    "http://192.168.1.1/x",
    "http://172.16.0.1/x",
    "http://0.0.0.0/x",                          # 未指定
    "http://224.0.0.1/x",                        # 组播
    "http://255.255.255.255/x",                  # 广播
    "http://evil.example.com/x",                 # 非回环域名（未配置白名单，主机名闸门即拒）
    "http://user:pass@127.0.0.1:8080/x",         # 内嵌凭据
    "file:///etc/passwd",                        # 非 http 协议
    "gopher://localhost:8080/_x",
    "ftp://localhost/x",
])
def test_default_mode_rejects_dangerous(url: str) -> None:
    assert not guard().is_safe(url)


def test_default_mode_rejects_loopback_name_resolving_to_private(monkeypatch: pytest.MonkeyPatch) -> None:
    # localhost 解析出非回环地址（本地 DNS/代理劫持等）必须拒绝
    monkeypatch.setattr(
        socket, "getaddrinfo",
        lambda host, port=None, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.5", 0))],
    )
    assert not guard().is_safe("http://localhost:8080/x")


# ---------- 白名单模式（生产推荐）：origin 精确匹配 ----------

def test_allowlist_matches_exact_origin() -> None:
    g = guard(["http://admin-backend:8080"])
    assert g.is_safe("http://admin-backend:8080/api/ai/callback/task")
    assert g.is_safe("http://ADMIN-BACKEND:8080/other/path")  # 主机大小写不敏感


def test_allowlist_folds_default_port() -> None:
    # :80/:443 归一化折叠，http://admin.example.com 与 http://admin.example.com:80 等价
    assert guard(["http://admin.example.com"]).is_safe("http://admin.example.com:80/api/x")
    assert guard(["https://admin.example.com:443"]).is_safe("https://admin.example.com/x")


@pytest.mark.parametrize("url", [
    "http://admin-backend:8081/api/ai/callback/task",   # 端口不同
    "http://other.example.com/api/ai/callback/task",    # 主机不同
    "http://127.0.0.1:8080/x",                          # 未列入白名单的回环
    "http://10.0.0.1/x",                                # 未列入白名单的私网
    "http://169.254.169.254/latest/meta-data/",         # 危险地址即使（误）列入也拒绝
    "http://user:pass@admin-backend:8080/x",            # 内嵌凭据
])
def test_allowlist_rejects_other_or_dangerous(url: str) -> None:
    assert not guard(["http://admin-backend:8080"]).is_safe(url)


def test_allowlist_rejects_dangerous_ip_even_when_explicitly_listed() -> None:
    # 硬性危险段优先级高于白名单：即使运维误把元数据地址写入白名单也必须拒绝
    assert not guard(["http://169.254.169.254"]).is_safe("http://169.254.169.254/latest/meta-data/")


def test_allowlist_blocks_dns_rebinding_to_metadata(monkeypatch: pytest.MonkeyPatch) -> None:
    # 白名单命中后，域名解析出危险段 IP（DNS 重绑定）仍拒绝
    monkeypatch.setattr(
        socket, "getaddrinfo",
        lambda host, port=None, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("169.254.169.254", 0))],
    )
    assert not guard(["http://evil.example.com:8080"]).is_safe("http://evil.example.com:8080/cb")


def test_allowlist_resolution_failure_is_fail_open() -> None:
    # 白名单 origin 匹配但域名解析失败（集群内 Service 未就绪）：不阻断，交由回调重试
    assert guard(["http://no-such-service.ns.svc:8080"]).is_safe("http://no-such-service.ns.svc:8080/cb")


def test_allowlist_allows_benign_resolved_ip(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        socket, "getaddrinfo",
        lambda host, port=None, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.5", 0))],
    )
    assert guard(["http://admin-backend:8080"]).is_safe("http://admin-backend:8080/cb")


# ---------- 配置校验 ----------

def test_invalid_allowlist_origin_fails_fast() -> None:
    with pytest.raises(ValueError):
        guard(["ftp://admin-backend:8080"])


def test_validate_raises_value_error_with_reason() -> None:
    with pytest.raises(ValueError, match="元数据|危险|回环|白名单"):
        guard().validate("http://169.254.169.254/latest/meta-data/")
    # 合法地址不抛异常
    guard().validate("http://127.0.0.1:8080/api/ai/callback/task")


# ---------- 建单 fail-fast 集成 ----------

@pytest.mark.asyncio
async def test_create_task_rejects_unsafe_callback_url() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())
    with pytest.raises(HTTPException) as excinfo:
        await manager.create_task(TaskCreateRequest(
            task_no="task-ssrf",
            biz_type="text_summary",
            params={},
            callback_url="http://169.254.169.254/latest/meta-data/",
        ))
    assert excinfo.value.status_code == 400


@pytest.mark.asyncio
async def test_create_task_accepts_safe_callback_url() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())
    task = await manager.create_task(TaskCreateRequest(
        task_no="task-safe",
        biz_type="text_summary",
        params={},
        callback_url="http://127.0.0.1:8080/api/ai/callback/task",
    ))
    # TaskStatusResponse 不含 callback_url 字段（extra=ignore），建单成功即可
    assert task.task_no == "task-safe"
    assert task.status == "QUEUED"


@pytest.mark.asyncio
async def test_callback_skips_unsafe_url_without_posting() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings())

    client_instances: list[str] = []

    class RecordingClient:
        def __init__(self, **kwargs):
            client_instances.append(str(kwargs))

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def post(self, url, json=None, headers=None):
            raise AssertionError(f"不应发起 POST: {url}")

    with patch("app.tasks.manager.httpx.AsyncClient", RecordingClient):
        await manager._callback("task-cb", {
            "callback_url": "http://169.254.169.254/latest/meta-data/",
            "biz_type": "text_summary",
            "status": "SUCCEEDED",
            "result": {"summary": "ok"},
            "error": None,
        })
    # 不安全地址直接跳过，未实例化 HTTP 客户端即未发起任何回调请求
    assert client_instances == []
