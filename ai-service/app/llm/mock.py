"""确定性 Mock 大模型：开箱即用、测试可重复，未配置真实 LLM 时兜底。

对话/工具/Embedding 均为纯函数式确定性行为，方便单测与本地联调：
- ``chat``：echo 前缀原样返回；含 "summarize" 返回固定摘要句；否则返回带 Mock 标记的拼接。
- ``stream``：将 ``chat`` 结果按两个字符一段产出，便于测流式管道。
- ``chat_with_tools``：最后一条用户消息以 ``calculate <表达式>`` 开头 → 请求 calculator 工具；
  ``search <关键词>`` 开头 → 请求 kb_search 工具；若消息历史已含 tool 结果 → 输出最终答复。
- ``embed``：字符 bigram 哈希词袋 → 固定 16 维归一化向量（同文同向量，语义相近文本余弦相近）。
"""

from __future__ import annotations

import hashlib
import math
from typing import AsyncIterator

from app.llm.base import LLMResponse, ToolCall

EMBED_DIM = 16


class MockProvider:
    name = "mock"

    async def chat(
        self,
        messages: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> str:
        last_user = self._last_user_content(messages)
        if not last_user:
            return "（无用户输入）"
        return self._reply(last_user)

    async def stream(
        self,
        messages: list[dict],
        model: str | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> AsyncIterator[str]:
        reply = await self.chat(messages, model, temperature, max_tokens)
        for i in range(0, len(reply), 2):
            yield reply[i : i + 2]

    async def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        model: str | None = None,
        temperature: float | None = None,
    ) -> LLMResponse:
        if any(m.get("role") == "tool" for m in messages):
            # 工具已执行：基于最后一条 tool 结果给出最终答复
            result = str(self._last_role_content(messages, "tool"))
            return LLMResponse(content=f"根据工具结果：{result}", model=model)
        last_user = self._last_user_content(messages)
        if last_user and last_user.startswith("calculate "):
            expr = last_user[len("calculate ") :].strip()
            return LLMResponse(content=None, tool_calls=[ToolCall("calculator", {"expression": expr})], model=model)
        if last_user and last_user.startswith("search "):
            query = last_user[len("search ") :].strip()
            return LLMResponse(content=None, tool_calls=[ToolCall("kb_search", {"query": query})], model=model)
        return LLMResponse(content=self._reply(last_user or ""), model=model)

    async def embed(self, texts: list[str], model: str | None = None) -> list[list[float]]:
        vectors: list[list[float]] = []
        for text in texts:
            vector = [0.0] * EMBED_DIM
            for i in range(len(text) - 1):
                bigram = text[i : i + 2]
                bucket = int(hashlib.md5(bigram.encode("utf-8")).hexdigest()[:8], 16) % EMBED_DIM
                vector[bucket] += 1.0
            norm = math.sqrt(sum(v * v for v in vector))
            vectors.append([v / norm if norm else 0.0 for v in vector])
        return vectors

    # ---------- 内部 ----------

    def _reply(self, last_user: str) -> str:
        if last_user.startswith("echo "):
            return last_user[len("echo ") :]
        if "summarize" in last_user or "摘要" in last_user:
            return "（Mock 摘要）第一句。第二句。"
        return f"[Mock] {last_user}"

    @staticmethod
    def _last_user_content(messages: list[dict]) -> str:
        for m in reversed(messages):
            if m.get("role") == "user":
                return str(m.get("content", ""))
        return ""

    @staticmethod
    def _last_role_content(messages: list[dict], role: str) -> str:
        for m in reversed(messages):
            if m.get("role") == role:
                return str(m.get("content", ""))
        return ""
