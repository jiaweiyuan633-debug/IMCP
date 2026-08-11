"""整链路冒烟测试：以 fakeredis 替换连接，跑通 lifespan 装配与全部新接口。

覆盖：健康检查、对话（Mock）、SSE 流式、Embedding、向量入库/检索、
任务队列（提交→终态）、定时管道注册/查询/删除、鉴权拒绝。
这是批次3 集成层（routes + services + 叶子模块 + TaskManager + Scheduler）
的唯一端到端验证；其余单测各自验证内部细节。
"""

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
    with _boot_client() as client:
        with client.stream(
            "POST",
            "/api/v1/chat/stream",
            json={"messages": [{"role": "user", "content": "echo 你好世界"}]},
            headers=AUTH,
        ) as response:
            assert response.status_code == 200
            body = response.read().decode("utf-8")
    assert "data: {\"delta\": \"你好\"}" in body
    assert "data: [DONE]" in body


def test_embeddings_dim() -> None:
    with _boot_client() as client:
        response = client.post("/api/v1/embeddings", json={"texts": ["甲", "乙"]}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["dim"] == 16
    assert len(body["vectors"]) == 2
    assert all(len(v) == 16 for v in body["vectors"])


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


def test_unauthorized_rejected() -> None:
    with _boot_client() as client:
        response = client.post("/api/v1/chat", json={"messages": [{"role": "user", "content": "hi"}]})
    assert response.status_code == 401


def test_bad_schedule_expression_rejected() -> None:
    with _boot_client() as client:
        response = client.post(
            "/api/v1/schedules",
            json={"name": "bad", "schedule": "every-minute", "biz_type": "text_summary", "params": {}},
            headers=AUTH,
        )
    assert response.status_code == 422
