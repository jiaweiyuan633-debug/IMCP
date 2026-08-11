"""智能体引擎：工具调用循环（agentic loop）。

流程：组装消息 → 携带工具声明调用模型 → 模型直接返回最终文本则结束；
否则逐条执行返回的工具调用、把调用与结果回填进消息历史，再次调用模型，
直到得到最终答复或达到最大步数（兜底输出 ``已达最大步数，未得到最终答复``）。
"""

from __future__ import annotations

import json
import time

from app.agents.tools import ToolRegistry
from app.llm.base import LLMProvider

# 达到最大步数仍未收敛时的兜底回复
_MAX_STEPS_MSG = "已达最大步数，未得到最终答复"


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

    def __init__(self, provider: LLMProvider, registry: ToolRegistry) -> None:
        self._provider = provider
        self._registry = registry

    async def run(self, params: dict) -> dict:
        """执行一次对话任务。

        参数（均可选）：``system_prompt``、``user_prompt``、``max_steps``（默认 5）、
        ``model``、``temperature``。返回 ``{"final", "steps", "steps_used", "duration_ms"}``。
        """
        system_prompt = str(params.get("system_prompt") or "").strip()
        user_prompt = str(params.get("user_prompt") or "")
        max_steps = int(params.get("max_steps") or 5)
        model = params.get("model")
        temperature = params.get("temperature")

        messages: list[dict] = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": user_prompt})
        tools_schema = _tools_schema(self._registry)

        steps: list[dict] = []
        final = ""
        call_seq = 0
        started = time.perf_counter()

        while True:
            response = await self._provider.chat_with_tools(
                messages,
                tools_schema,
                model=model,
                temperature=temperature,
            )
            # 1) 模型直接给出最终答复，结束循环
            if response.content:
                final = response.content
                break
            # 2) 模型请求工具：先校验步数上限，再逐条执行并回填消息
            if response.tool_calls:
                if len(steps) >= max_steps:
                    final = _MAX_STEPS_MSG
                    break
                for call in response.tool_calls:
                    call_seq += 1
                    call_id = f"call_{call_seq}"
                    try:
                        result = await self._registry.call(call.name, call.arguments)
                    except Exception as exc:  # noqa: BLE001 —— 工具异常不中断循环，转为文本交给模型
                        result = str(exc)
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
