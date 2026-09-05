"""智能体引擎：工具调用循环（agentic loop）。

流程：组装消息 → 携带工具声明调用模型 → 模型直接返回最终文本则结束；
否则逐条执行返回的工具调用、把调用与结果回填进消息历史，再次调用模型，
直到得到最终答复或达到最大步数（兜底输出 ``已达最大步数，未得到最终答复``）。

出域脱敏：system/user 提示词、工具执行结果与最终答复最终都会发给外部 LLM，
可注入 ``mask_text``（如 app.pii.mask）在写入消息历史 / 返回前脱敏。
"""

from __future__ import annotations

import json
import time
from collections.abc import Callable

from app.agents.tools import ToolRegistry
from app.llm.base import LLMProvider
from app.llm.retry import retry_llm_call

# 达到最大步数仍未收敛时的兜底回复
_MAX_STEPS_MSG = "已达最大步数，未得到最终答复"
# 单轮模型响应允许的最大工具调用数：防模型一次返回海量 tool_calls 撑爆消息历史
_MAX_TOOL_CALLS_PER_TURN = 20
# 消息历史长度上限（system + user + 每步约 2 条 assistant/tool），防无界增长
_MAX_HISTORY_MESSAGES = 200


def _tools_schema(registry: ToolRegistry) -> list[dict]:
    """由注册表汇总生成 OpenAI 风格的函数声明列表。"""
    return [
        {
            "type": "function",
            "function": {
                "name": item["name"],
                "description": item["description"],
                "parameters": {"type": "object", "properties": {}},
            },
        }
        for item in registry.summaries()
    ]


class AgentEngine:
    """驱动大模型 + 工具完成多轮调用，直到产出最终答复。"""

    def __init__(
        self,
        provider: LLMProvider,
        registry: ToolRegistry,
        mask_text: Callable[[str], str] | None = None,
    ) -> None:
        self._provider = provider
        self._registry = registry
        # 出域脱敏回调（同步函数）；None 表示不脱敏
        self._mask_text = mask_text

    def _mask(self, text: str) -> str:
        return self._mask_text(text) if self._mask_text is not None else text

    async def run(self, params: dict) -> dict:
        """执行一次对话任务。

        参数（均可选）：``system_prompt``、``user_prompt``、``max_steps``（默认 5，
        强制上限 50）、``model``、``temperature``。返回 ``{"final", "steps", "steps_used", "duration_ms"}``。
        """
        system_prompt = str(params.get("system_prompt") or "").strip()
        user_prompt = str(params.get("user_prompt") or "")
        max_steps = min(max(1, int(params.get("max_steps") or 5)), 50)
        model = params.get("model")
        temperature = params.get("temperature")

        messages: list[dict] = []
        if system_prompt:
            messages.append({"role": "system", "content": self._mask(system_prompt)})
        messages.append({"role": "user", "content": self._mask(user_prompt)})
        tools_schema = _tools_schema(self._registry)

        steps: list[dict] = []
        final = ""
        call_seq = 0
        started = time.perf_counter()

        while True:
            # 非任务路径：单次模型调用失败（网络/5xx）会整轮失败，对可重试异常
            # 做指数退避重试（LLMConfigError 立即上抛）
            response = await retry_llm_call(
                self._provider.chat_with_tools,
                messages,
                tools_schema,
                model=model,
                temperature=temperature,
            )
            # 1) 模型直接给出最终答复，结束循环
            if response.content:
                final = self._mask(response.content)
                break
            # 2) 模型请求工具：先校验步数上限，再逐条执行并回填消息
            if response.tool_calls:
                if len(steps) >= max_steps:
                    final = _MAX_STEPS_MSG
                    break
                for call in response.tool_calls[:_MAX_TOOL_CALLS_PER_TURN]:
                    if len(messages) >= _MAX_HISTORY_MESSAGES:
                        break
                    call_seq += 1
                    call_id = f"call_{call_seq}"
                    try:
                        raw_result = await self._registry.call(call.name, call.arguments)
                    except Exception as exc:  # noqa: BLE001 —— 工具异常不中断循环，转为文本交给模型
                        raw_result = str(exc)
                    # 工具结果（可能含知识库原文 PII）脱敏后回填消息历史与 steps
                    result = self._mask(str(raw_result))
                    messages.append({
                        "role": "assistant",
                        "tool_calls": [{
                            "id": call_id,
                            "type": "function",
                            "function": {
                                "name": call.name,
                                "arguments": json.dumps(call.arguments, ensure_ascii=False),
                            },
                        }],
                    })
                    messages.append({
                        "role": "tool",
                        "tool_call_id": call_id,
                        "content": result,
                    })
                    steps.append({"tool": call.name, "args": call.arguments, "result": result})
                continue
            # 3) 既无文本也无工具调用：视为无可用输出，终止循环
            final = ""
            break

        duration_ms = int((time.perf_counter() - started) * 1000)
        return {
            "final": final,
            "steps": steps,
            "steps_used": len(steps),
            "duration_ms": duration_ms,
        }
