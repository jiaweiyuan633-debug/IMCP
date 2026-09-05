"""出域 PII 强制脱敏回归：PII_MASK_REQUIRED=true 时客户端 mask_pii=false 不绕过。

覆盖统一出口（app.pii.outbound）的开关语义与 HTTP 路径：
- /chat/stream 入站消息发给 provider 前脱敏（mask_pii=false 亦不绕过）；
- /embeddings 待向量化文本出域前脱敏；
- mock（进程内）提供方豁免强制，本地联调/测试不受影响。
"""

import json
from types import SimpleNamespace

import fakeredis.aioredis
from fastapi.testclient import TestClient

import app.main as main_module
from app.core.config import settings
from app.pii.outbound import should_mask_outbound

main_module.Redis = fakeredis.aioredis.FakeRedis

AUTH = {"Authorization": f"Bearer {settings.auth_token}"}

_MOCK = SimpleNamespace(name="mock")
_EXT = SimpleNamespace(name="deepseek-ext")


def _sse_deltas(body: str) -> list[str]:
    deltas: list[str] = []
    for line in body.splitlines():
        if not line.startswith("data: "):
            continue
        payload = line[len("data: ") :].strip()
        if payload == "[DONE]":
            continue
        deltas.append(json.loads(payload)["delta"])
    return deltas


# ---------- 开关判定（共享出口） ----------


def test_should_mask_outbound_follows_request_when_not_required() -> None:
    cfg = SimpleNamespace(pii_mask_required=False)
    assert should_mask_outbound(cfg, _EXT, True) is True
    assert should_mask_outbound(cfg, _EXT, False) is False
    assert should_mask_outbound(cfg, _MOCK, True) is True
    assert should_mask_outbound(cfg, _MOCK, False) is False


def test_should_mask_outbound_required_overrides_false() -> None:
    cfg = SimpleNamespace(pii_mask_required=True)
    assert should_mask_outbound(cfg, _EXT, False) is True
    assert should_mask_outbound(cfg, _EXT, True) is True
    # mock 提供方在进程内完成、无跨进程出域，豁免强制
    assert should_mask_outbound(cfg, _MOCK, False) is False


# ---------- HTTP 路径强制脱敏（注册的非 mock 假 provider 观察入站文本） ----------


class RecordingProvider:
    """记录入站文本/消息的假外部 provider（name 非 mock，模拟真实出域）。"""

    name = "recording-ext"

    def __init__(self) -> None:
        self.seen_texts: list[str] = []
        self.seen_messages: list[dict] = []

    async def embed(self, texts, model=None):
        self.seen_texts.extend(texts)
        return [[0.0] * 4 for _ in texts]

    async def chat(self, messages, model=None, temperature=None, max_tokens=None):
        self.seen_messages.extend(messages)
        return "ok"

    async def stream(self, messages, model=None, temperature=None, max_tokens=None):
        self.seen_messages.extend(messages)
        last = ""
        for m in reversed(messages):
            if m.get("role") == "user":
                last = str(m.get("content", ""))
                break
        for i in range(0, len(last), 2):
            yield last[i : i + 2]


def _boot_client():
    return TestClient(main_module.app)


def test_chat_stream_masks_outbound_even_when_mask_pii_false(monkeypatch) -> None:
    monkeypatch.setattr(settings, "pii_mask_required", True)
    recorder = RecordingProvider()
    with _boot_client() as client:
        client.app.state.providers.register(recorder.name, recorder)
        with client.stream(
            "POST",
            "/api/v1/chat/stream",
            json={
                "messages": [{"role": "user", "content": "echo 联系 13812345678"}],
                "mask_pii": False,
                "provider": recorder.name,
            },
            headers=AUTH,
        ) as response:
            body = response.read().decode("utf-8")
    # provider 收到的入站消息不含原始手机号（修复前 /chat/stream 入站不脱敏）
    user_msgs = [m for m in recorder.seen_messages if m.get("role") == "user"]
    assert user_msgs
    assert "13812345678" not in user_msgs[-1]["content"]
    assert "***********" in user_msgs[-1]["content"]
    # 输出侧拼装同样不含原始手机号
    assert "13812345678" not in "".join(_sse_deltas(body))


def test_embeddings_masks_outbound_even_when_mask_pii_false(monkeypatch) -> None:
    monkeypatch.setattr(settings, "pii_mask_required", True)
    recorder = RecordingProvider()
    with _boot_client() as client:
        client.app.state.providers.register(recorder.name, recorder)
        response = client.post(
            "/api/v1/embeddings",
            json={"texts": ["联系 13812345678"], "provider": recorder.name, "mask_pii": False},
            headers=AUTH,
        )
    assert response.status_code == 200
    assert response.json()["dim"] == 4
    # 发给 embedding provider 的文本已被脱敏（mask_pii=false 不绕过）
    assert recorder.seen_texts
    assert "13812345678" not in recorder.seen_texts[0]
    assert "***********" in recorder.seen_texts[0]


def test_mask_pii_false_still_passthrough_when_not_required(monkeypatch) -> None:
    """未开启强制时，mask_pii=false 保持原有「显式放行」语义（向后兼容）。"""
    monkeypatch.setattr(settings, "pii_mask_required", False)
    with _boot_client() as client:
        response = client.post(
            "/api/v1/chat",
            json={"messages": [{"role": "user", "content": "echo 手机 13812345678"}], "mask_pii": False},
            headers=AUTH,
        )
    assert response.status_code == 200
    assert response.json()["content"] == "手机 13812345678"
