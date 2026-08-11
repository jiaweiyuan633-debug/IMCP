"""OpenAI 兼容大模型提供方：对话 / 流式 SSE / 工具调用 / Embedding。

协议兼容 OpenAI Chat Completions 与 Embeddings，因此一个实现即可对接
OpenAI、DeepSeek、通义千问、Moonshot、Ollama、vLLM、LM Studio 等绝大多数服务。
网络错误与上游 5xx 抛 ``LLMError``（可重试）；认证/模型不存在（401/403/404）抛 ``LLMConfigError``（不可重试）。
"""

from __future__ import annotations

import json
from typing import AsyncIterator

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
        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds, trust_env=False) as client:
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
        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds, trust_env=False) as client:
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
        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds, trust_env=False) as client:
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
