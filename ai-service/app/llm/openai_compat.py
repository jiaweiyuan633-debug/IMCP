"""OpenAI 兼容大模型提供方：对话 / 流式 SSE / 工具调用 / Embedding。

协议兼容 OpenAI Chat Completions 与 Embeddings，因此一个实现即可对接
OpenAI、DeepSeek、通义千问、Moonshot、Ollama、vLLM、LM Studio 等绝大多数服务。
网络错误与上游 5xx 抛 ``LLMError``（可重试）；认证/模型不存在（401/403/404）抛 ``LLMConfigError``（不可重试）。

连接复用——provider 持有单个持久 ``httpx.AsyncClient``（连接池），
所有调用复用同一池，避免每次请求重建 TCP 连接与 TLS 握手；随服务关闭（``aclose``）释放。
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator

import httpx

from app.llm.base import LLMConfigError, LLMError, LLMResponse, ToolCall

CHAT_PATH = "/v1/chat/completions"
EMBED_PATH = "/v1/embeddings"


class OpenAICompatibleProvider:
    name = "openai-compatible"

    def __init__(
        self,
        base_url: str,
        api_key: str = "",
        default_model: str | None = None,
        embedding_model: str | None = None,
        timeout_seconds: int = 120,
        name: str = "openai-compatible",
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.default_model = default_model
        self.embedding_model = embedding_model
        self.timeout_seconds = timeout_seconds
        self.name = name
        # 持久复用单个 AsyncClient（连接池 + TLS 会话复用）。此前每次调用
        # 都新建/销毁 client，每个请求都重建 TCP 连接与 TLS 握手；连接池在长连接
        # keep-alive 下复用同一连接。惰性创建：首个请求才实例化（__init__ 可能
        # 在事件循环外执行，httpx 连接池构造需在运行中的循环内进行）。
        self._client: httpx.AsyncClient | None = None

    async def chat(
        self,
        messages: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> str:
        response = await self.chat_with_tools(messages, [], model, temperature, max_tokens)
        if response.content is None:
            raise LLMError("模型未返回文本内容")
        return response.content

    async def stream(
        self,
        messages: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> AsyncIterator[str]:
        resolved_model = model or self.default_model
        if not resolved_model:
            raise LLMConfigError("未配置模型名称")
        body: dict = {
            "model": resolved_model,
            "messages": messages,
            "stream": True,
        }
        if temperature is not None:
            body["temperature"] = temperature
        if max_tokens is not None:
            body["max_tokens"] = max_tokens
        client = self._get_client()
        try:
            async with client.stream("POST", self.base_url + CHAT_PATH, json=body, headers=self._headers()) as resp:
                if resp.status_code in (401, 403, 404):
                    raise LLMConfigError(f"模型请求被拒绝: HTTP {resp.status_code}")
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    data = line[len("data:") :].strip()
                    if data == "[DONE]":
                        break
                    delta = self._extract_delta(data)
                    if delta:
                        yield delta
        except httpx.HTTPError as exception:
            raise LLMError(f"模型流式请求失败: {exception}") from exception

    async def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> LLMResponse:
        resolved_model = model or self.default_model
        if not resolved_model:
            raise LLMConfigError("未配置模型名称")
        body: dict = {"model": resolved_model, "messages": messages, "stream": False}
        if tools:
            body["tools"] = tools
            body["tool_choice"] = "auto"
        if temperature is not None:
            body["temperature"] = temperature
        if max_tokens is not None:
            body["max_tokens"] = max_tokens
        client = self._get_client()
        try:
            resp = await client.post(self.base_url + CHAT_PATH, json=body, headers=self._headers())
            if resp.status_code in (401, 403, 404):
                raise LLMConfigError(f"模型请求被拒绝: HTTP {resp.status_code}")
            resp.raise_for_status()
            payload = resp.json()
        except httpx.HTTPError as exception:
            raise LLMError(f"模型请求失败: {exception}") from exception
        return self._parse_choices(payload, resolved_model)

    async def embed(self, texts: list[str], model: str | None = None) -> list[list[float]]:
        if not texts:
            return []
        resolved_model = model or self.embedding_model
        if not resolved_model:
            raise LLMConfigError("未配置 embedding_model")
        client = self._get_client()
        try:
            resp = await client.post(
                self.base_url + EMBED_PATH,
                json={"model": resolved_model, "input": texts},
                headers=self._headers(),
            )
            if resp.status_code in (401, 403, 404):
                raise LLMConfigError(f"Embedding 请求被拒绝: HTTP {resp.status_code}")
            resp.raise_for_status()
            payload = resp.json()
        except httpx.HTTPError as exception:
            raise LLMError(f"Embedding 请求失败: {exception}") from exception
        ordered = sorted(payload.get("data", []), key=lambda item: int(item.get("index", 0)))
        return [item["embedding"] for item in ordered]

    # ---------- 连接池生命周期 ----------

    def _get_client(self) -> httpx.AsyncClient:
        """惰性创建并复用持久 client：首个请求实例化，随服务关闭（``aclose``）销毁。

        连接池复用 TCP 连接与 TLS 会话，避免每个请求重建握手。provider 为进程内
        单例、运行在单一事件循环上，满足 httpx 连接池线程安全约束。
        """
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=self.timeout_seconds, trust_env=False)
        return self._client

    async def aclose(self) -> None:
        """释放连接池（应用关闭时调用）。未创建/已关闭时为 no-op。

        用 ``getattr`` 兜底：测试环境替换的假 client 可能无 ``aclose``。
        """
        client, self._client = self._client, None
        if client is not None:
            closer = getattr(client, "aclose", None)
            if closer is not None:
                await closer()

    # ---------- 内部 ----------

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        return headers

    @staticmethod
    def _extract_delta(line: str) -> str | None:
        try:
            data = json.loads(line)
        except json.JSONDecodeError:
            return None
        choices = data.get("choices") or []
        if not choices:
            return None
        delta = choices[0].get("delta") or {}
        content = delta.get("content")
        return content if isinstance(content, str) else None

    def _parse_choices(self, payload: dict, model: str) -> LLMResponse:
        choices = payload.get("choices") or []
        if not choices:
            raise LLMError("模型响应缺少 choices")
        message = choices[0].get("message") or {}
        content = message.get("content")
        tool_calls = []
        for call in message.get("tool_calls") or []:
            fn = call.get("function") or {}
            args = fn.get("arguments") or ""
            try:
                parsed_args = json.loads(args) if args else {}
            except json.JSONDecodeError:
                parsed_args = {"raw": args}
            tool_calls.append(ToolCall(name=fn.get("name", ""), arguments=parsed_args))
        return LLMResponse(
            content=content if isinstance(content, str) else None,
            tool_calls=tool_calls,
            model=model,
            usage=payload.get("usage"),
        )
