"""整链路冒烟测试：以 fakeredis 替换连接，跑通 lifespan 装配与全部新接口。

覆盖：健康检查、对话（Mock）、SSE 流式、Embedding、向量入库/检索、
任务队列（提交→终态）、定时管道注册/查询/删除、鉴权拒绝。
这是集成层（routes + services + 叶子模块 + TaskManager + Scheduler）
的唯一端到端验证；其余单测各自验证内部细节。
"""

import json
import time

import fakeredis.aioredis
from fastapi.testclient import TestClient

import app.main as main_module
from app.core.config import settings

AUTH = {"Authorization": f"Bearer {settings.auth_token}"}

# 让 lifespan 里的 Redis.from_url(...) 返回 fakeredis，避免依赖真实 Redis
main_module.Redis = fakeredis.aioredis.FakeRedis


def _boot_client():
    return TestClient(main_module.app)


def _sse_deltas(body: str) -> list[str]:
    """解析 SSE 响应体，拼装全部 ``data: {"delta": ...}`` 内容（跳过 [DONE]）。

    PII 强制会重排分片边界（StreamMasker 滚动缓冲），消费方应拼装全文——
    断言按 delta 全文拼装，而非锁定某个具体分片。
    """
    deltas: list[str] = []
    for line in body.splitlines():
        if not line.startswith("data: "):
            continue
        payload = line[len("data: ") :].strip()
        if payload == "[DONE]":
            continue
        deltas.append(json.loads(payload)["delta"])
    return deltas


def test_health_ok_through_lifespan() -> None:
    with _boot_client() as client:
        response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ai-service"}


def test_chat_mock_provider() -> None:
    with _boot_client() as client:
        response = client.post("/api/v1/chat", json={"messages": [{"role": "user", "content": "hello"}]}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["content"] == "[Mock] hello"
    assert body["provider"] == "mock"


def test_chat_stream_sse() -> None:
    with _boot_client() as client, client.stream(
        "POST",
        "/api/v1/chat/stream",
        json={"messages": [{"role": "user", "content": "echo 你好世界"}]},
        headers=AUTH,
    ) as response:
        assert response.status_code == 200
        body = response.read().decode("utf-8")
    # PII 强制（默认开）经滚动缓冲可能重排分片边界，按 delta 拼装断言全文
    assert "".join(_sse_deltas(body)) == "你好世界"
    assert "data: [DONE]" in body


def test_embeddings_dim() -> None:
    with _boot_client() as client:
        response = client.post("/api/v1/embeddings", json={"texts": ["甲", "乙"]}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["dim"] == 16
    assert len(body["vectors"]) == 2
    assert all(len(v) == 16 for v in body["vectors"])


def test_unknown_provider_returns_400() -> None:
    """未知 provider 属客户端错误返回 400，而非 KeyError 导致的 500。

    修复前 ProviderRegistry.get 抛 KeyError、FastAPI 未捕获 → 500；调用方无法
    区分「服务端故障」与「provider 拼错」两种语义，也无法据此修正参数。
    """
    with _boot_client() as client:
        chat = client.post(
            "/api/v1/chat",
            json={"messages": [{"role": "user", "content": "hi"}], "provider": "nope"},
            headers=AUTH,
        )
        stream = client.post(
            "/api/v1/chat/stream",
            json={"messages": [{"role": "user", "content": "hi"}], "provider": "nope"},
            headers=AUTH,
        )
        embed = client.post(
            "/api/v1/embeddings",
            json={"texts": ["hi"], "provider": "nope"},
            headers=AUTH,
        )
        upsert = client.post(
            "/api/v1/vectors/upsert",
            json={"namespace": "kb", "doc_id": "d", "text": "hi", "provider": "nope"},
            headers=AUTH,
        )
    assert chat.status_code == 400
    assert chat.json()["detail"] == "unknown provider: nope"
    assert stream.status_code == 400
    assert embed.status_code == 400
    assert upsert.status_code == 400


def test_vector_upsert_and_search_roundtrip() -> None:
    with _boot_client() as client:
        up = client.post(
            "/api/v1/vectors/upsert",
            json={"namespace": "kb", "doc_id": "doc-1", "text": "智能制造管理平台", "payload": {"title": "平台"}},
            headers=AUTH,
        )
        assert up.status_code == 200
        assert up.json()["dim"] == 16

        search = client.post(
            "/api/v1/vectors/search",
            json={"namespace": "kb", "text": "智能制造管理平台", "top_k": 1, "threshold": 0.5},
            headers=AUTH,
        )
    assert search.status_code == 200
    hits = search.json()["hits"]
    assert len(hits) == 1
    assert hits[0]["doc_id"] == "doc-1"
    assert hits[0]["payload"]["title"] == "平台"
    assert hits[0]["score"] > 0.5


def test_task_submit_reaches_succeeded() -> None:
    with _boot_client() as client:
        created = client.post(
            "/api/v1/tasks",
            json={"task_no": "smoke-1", "biz_type": "text_summary", "params": {"content": "第一句。第二句。"}},
            headers=AUTH,
        )
        assert created.status_code == 202
        assert created.json()["status"] == "QUEUED"

        final = None
        for _ in range(200):
            response = client.get("/api/v1/tasks/smoke-1", headers=AUTH)
            final = response.json()
            if final["status"] in ("SUCCEEDED", "FAILED"):
                break
            time.sleep(0.02)
    assert final is not None
    assert final["status"] == "SUCCEEDED"
    assert "summary" in final["result"]


def test_schedule_crud() -> None:
    with _boot_client() as client:
        created = client.post(
            "/api/v1/schedules",
            json={"name": "每日摘要", "schedule": "interval:3600", "biz_type": "text_summary", "params": {"content": "x"}},
            headers=AUTH,
        )
        assert created.status_code == 200
        schedule_id = created.json()["id"]
        assert created.json()["next_run_at"] is not None

        listed = client.get("/api/v1/schedules", headers=AUTH)
        assert any(item["id"] == schedule_id for item in listed.json())

        deleted = client.delete(f"/api/v1/schedules/{schedule_id}", headers=AUTH)
        assert deleted.status_code == 200

        listed_after = client.get("/api/v1/schedules", headers=AUTH)
        assert all(item["id"] != schedule_id for item in listed_after.json())


def test_chat_masks_pii_by_default() -> None:
    """PII 强制默认开启——user 消息出站前先脱敏（手机号不落外部 LLM），
    模型复述的敏感信息出站前再脱敏。输入 PII 已在出站层脱敏，输出自然不含原始号码。"""
    with _boot_client() as client:
        response = client.post(
            "/api/v1/chat",
            json={"messages": [{"role": "user", "content": "echo 手机 13812345678"}], "mask_pii": True},
            headers=AUTH,
        )
    assert response.status_code == 200
    body = response.json()
    # 出站脱敏：模型收到的输入已无原始手机号，输出 echo 亦不含
    assert "13812345678" not in body["content"]


def test_chat_pii_can_be_explicitly_disabled() -> None:
    """mask_pii=False 时原文透出（明确关停的调用放行）。"""
    with _boot_client() as client:
        response = client.post(
            "/api/v1/chat",
            json={"messages": [{"role": "user", "content": "echo 手机 13812345678"}], "mask_pii": False},
            headers=AUTH,
        )
    assert response.json()["content"] == "手机 13812345678"
    assert response.json()["pii_count"] == 0


def test_chat_stream_masks_split_pii() -> None:
    """流式 PII 强制：手机号被拆到多个 2 字符 delta，仍被完整脱敏（跨分片不泄漏）。"""
    with _boot_client() as client, client.stream(
        "POST",
        "/api/v1/chat/stream",
        json={"messages": [{"role": "user", "content": "echo 13812345678"}], "mask_pii": True},
        headers=AUTH,
    ) as response:
        body = response.read().decode("utf-8")
    assert "".join(_sse_deltas(body)) == "***********"


def test_chat_stream_pii_can_be_disabled() -> None:
    with _boot_client() as client, client.stream(
        "POST",
        "/api/v1/chat/stream",
        json={"messages": [{"role": "user", "content": "echo 13812345678"}], "mask_pii": False},
        headers=AUTH,
    ) as response:
        body = response.read().decode("utf-8")
    assert "".join(_sse_deltas(body)) == "13812345678"


def test_unauthorized_rejected() -> None:
    with _boot_client() as client:
        response = client.post("/api/v1/chat", json={"messages": [{"role": "user", "content": "hi"}]})
    assert response.status_code == 401


def test_dead_letter_api_endpoints_and_route_order() -> None:
    """/tasks/dead 字面量路由先于 /tasks/{task_id} 注册（否则被参数路由吞掉）。

    若注册顺序错误，GET /api/v1/tasks/dead 会被 {task_id} 捕获为查询任务
    "dead"（404 task not found），DELETE 则因无 DELETE /tasks/{task_id} 返回 405——
    下方任一断言都会失败。空队列即足以暴露顺序问题：正确注册时 GET 返回
    200 空数组、DELETE 返回 200 清理数 0。顺带验证 limit 查询参数被接受。
    """
    with _boot_client() as client:
        listed = client.get("/api/v1/tasks/dead", headers=AUTH)
        assert listed.status_code == 200
        assert listed.json() == []

        listed_limit = client.get("/api/v1/tasks/dead?limit=1", headers=AUTH)
        assert listed_limit.status_code == 200

        purged = client.delete("/api/v1/tasks/dead", headers=AUTH)
        assert purged.status_code == 200
        assert purged.json() == {"purged": 0}


def test_dead_letter_api_requires_token() -> None:
    with _boot_client() as client:
        response = client.get("/api/v1/tasks/dead")
    assert response.status_code == 401


def test_bad_schedule_expression_rejected() -> None:
    with _boot_client() as client:
        response = client.post(
            "/api/v1/schedules",
            json={"name": "bad", "schedule": "every-minute", "biz_type": "text_summary", "params": {}},
            headers=AUTH,
        )
    assert response.status_code == 422
