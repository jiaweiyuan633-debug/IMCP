import httpx
import pytest

from app.llm.base import LLMConfigError, LLMError
from app.llm.openai_compat import OpenAICompatibleProvider


class FakeAsyncClient:
    """替换 httpx.AsyncClient：按 URL/请求体返回预设响应。"""

    def __init__(self, **kwargs):
        self.kwargs = kwargs

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    def stream(self, *args, **kwargs):
        return _FakeStream(self._handler)

    async def post(self, *args, **kwargs):
        return await self._handler(*args, **kwargs)

    _handler = None


class _FakeStream:
    def __init__(self, handler):
        self.handler = handler

    async def __aenter__(self):
        self.response = await self.handler()
        return self.response

    async def __aexit__(self, exc_type, exc, traceback):
        return False


class _FakeResponse:
    def __init__(self, status_code, json_body=None, lines=None):
        self.status_code = status_code
        self._json = json_body or {}
        self._lines = lines or []

    def raise_for_status(self):
        if self.status_code >= 400:
            # 与真实 httpx 一致：抛出 HTTPStatusError，供提供方捕获并映射错误分类
            request = httpx.Request("POST", "https://example.com/v1/chat/completions")
            response = httpx.Response(self.status_code, request=request)
            response.raise_for_status()

    def json(self):
        return self._json

    async def aiter_lines(self):
        for line in self._lines:
            yield line


def _chat_body(choices_content="你好，我是助手", tool_calls=None):
    message = {"role": "assistant", "content": choices_content}
    if tool_calls:
        message["tool_calls"] = tool_calls
    return {"choices": [{"message": message}], "usage": {"total_tokens": 42}}


@pytest.mark.asyncio
async def test_chat_non_stream(monkeypatch) -> None:
    captured = {}

    async def handler(*args, **kwargs):
        captured.update(kwargs)
        return _FakeResponse(200, _chat_body("回复内容"))

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", FakeAsyncClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", api_key="sk", default_model="gpt")
    result = await provider.chat([{"role": "user", "content": "hi"}])

    assert result == "回复内容"
    assert captured["json"]["stream"] is False
    assert captured["json"]["model"] == "gpt"
    assert captured["headers"]["Authorization"] == "Bearer sk"


@pytest.mark.asyncio
async def test_chat_stream_yields_deltas(monkeypatch) -> None:
    lines = [
        "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
        "data: {\"choices\":[{\"delta\":{\"content\":\"世界\"}}]}",
        "data: [DONE]",
    ]

    async def handler(*args, **kwargs):
        return _FakeResponse(200, lines=lines)

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", FakeAsyncClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", default_model="gpt")
    deltas = []
    async for delta in provider.stream([{"role": "user", "content": "hi"}]):
        deltas.append(delta)

    assert deltas == ["你好", "世界"]


@pytest.mark.asyncio
async def test_chat_with_tools_parses_tool_call(monkeypatch) -> None:
    tool_calls = [{
        "id": "call-1",
        "type": "function",
        "function": {"name": "calculator", "arguments": "{\"expression\": \"1+2\"}"},
    }]

    async def handler(*args, **kwargs):
        return _FakeResponse(200, _chat_body(choices_content=None, tool_calls=tool_calls))

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", FakeAsyncClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", default_model="gpt")
    resp = await provider.chat_with_tools([{"role": "user", "content": "计算"}], [{"type": "function"}])

    assert resp.content is None
    assert resp.tool_calls[0].name == "calculator"
    assert resp.tool_calls[0].arguments == {"expression": "1+2"}


@pytest.mark.asyncio
async def test_embed_returns_ordered_vectors(monkeypatch) -> None:
    body = {"data": [
        {"index": 1, "embedding": [0.0, 1.0]},
        {"index": 0, "embedding": [1.0, 0.0]},
    ]}

    async def handler(*args, **kwargs):
        return _FakeResponse(200, body)

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", FakeAsyncClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", embedding_model="text-embed")
    vectors = await provider.embed(["甲", "乙"])

    assert vectors == [[1.0, 0.0], [0.0, 1.0]]


@pytest.mark.asyncio
async def test_http_401_maps_to_config_error(monkeypatch) -> None:
    async def handler(*args, **kwargs):
        return _FakeResponse(401)

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", FakeAsyncClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", default_model="gpt")
    with pytest.raises(LLMConfigError):
        await provider.chat([{"role": "user", "content": "hi"}])


@pytest.mark.asyncio
async def test_http_500_maps_to_retryable_error(monkeypatch) -> None:
    async def handler(*args, **kwargs):
        return _FakeResponse(500)

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", FakeAsyncClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", default_model="gpt")
    with pytest.raises(LLMError):
        await provider.chat([{"role": "user", "content": "hi"}])


@pytest.mark.asyncio
async def test_missing_model_is_config_error() -> None:
    provider = OpenAICompatibleProvider(base_url="https://example.com")
    with pytest.raises(LLMConfigError):
        await provider.chat([{"role": "user", "content": "hi"}])


# ---------- 连接复用与生命周期 ----------


@pytest.mark.asyncio
async def test_persistent_client_reused_across_calls(monkeypatch) -> None:
    """连接池复用：多次调用（chat + embed）只创建一个 AsyncClient 实例。

    修复前每次调用都 ``async with httpx.AsyncClient(...)`` 新建/销毁，每次请求
    重建 TCP 连接与 TLS 握手；持久 client 应跨调用复用同一连接池。
    """
    created: list[dict] = []

    class CountingClient(FakeAsyncClient):
        def __init__(self, **kwargs):
            super().__init__(**kwargs)
            created.append(kwargs)

    async def handler(*args, **kwargs):
        # FakeAsyncClient.post 把 self 作为首个位置参数透传，真实 URL 在 args[1]
        url = args[1] if len(args) > 1 else kwargs.get("url", "")
        if "embeddings" in url:
            return _FakeResponse(200, {"data": [{"index": 0, "embedding": [0.0, 1.0]}]})
        return _FakeResponse(200, _chat_body("ok"))

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", CountingClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", default_model="gpt", embedding_model="emb")
    await provider.chat([{"role": "user", "content": "hi"}])
    await provider.chat([{"role": "user", "content": "hi"}])
    await provider.embed(["hi"])

    assert len(created) == 1  # 三个调用共享同一个 client（连接池）
    assert created[0]["timeout"] == 120
    assert created[0]["trust_env"] is False


@pytest.mark.asyncio
async def test_aclose_releases_and_recreates_client(monkeypatch) -> None:
    """aclose 关闭连接池并置空；后续调用惰性重建新 client。"""
    created: list[str] = []
    closed: list[str] = []

    class TrackingClient(FakeAsyncClient):
        def __init__(self, **kwargs):
            super().__init__(**kwargs)
            created.append("client")

        async def aclose(self):
            closed.append("closed")

    async def handler(*args, **kwargs):
        return _FakeResponse(200, _chat_body("ok"))

    FakeAsyncClient._handler = handler
    monkeypatch.setattr("app.llm.openai_compat.httpx.AsyncClient", TrackingClient)

    provider = OpenAICompatibleProvider(base_url="https://example.com", default_model="gpt")
    await provider.chat([{"role": "user", "content": "hi"}])
    await provider.aclose()
    assert closed == ["closed"]

    # 关闭后再次调用：惰性重建新 client，但旧 client 只关闭过一次
    await provider.chat([{"role": "user", "content": "hi"}])
    assert len(created) == 2
    assert len(closed) == 1

    # aclose 幂等：未创建/已关闭时不抛错
    await provider.aclose()
    await provider.aclose()
