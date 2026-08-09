from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_health_reports_unhealthy_when_redis_down() -> None:
    redis = AsyncMock()
    redis.ping.side_effect = Exception("connection refused")
    app.state.redis = redis
    response = client.get("/health")
    assert response.status_code == 503
    assert response.json()["status"] == "error"
    assert "redis" in response.json()["detail"]


def test_ping() -> None:
    response = client.get("/api/v1/ping")
    assert response.status_code == 200
    assert response.json()["message"] == "pong"

