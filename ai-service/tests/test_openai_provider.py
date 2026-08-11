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
