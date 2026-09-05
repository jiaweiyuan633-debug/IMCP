"""智能体任务服务：biz_type=agent。

入参：{"system_prompt"?, "user_prompt", "max_steps"?, "provider"?}
出参：{"final", "steps", "steps_used", "duration_ms"}
"""

from __future__ import annotations

from typing import Any

from app.agents import AgentEngine, build_default_registry
from app.pii import mask
from app.pii.outbound import should_mask_outbound
from app.services.base import BaseTaskService
from app.services.context import ServiceContext


class AgentService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context
        # 默认用注册表默认提供方（未配置时即 Mock）；工具集含 calculator/echo/kb_search
        provider = context.providers.get(str(context.settings.llm_default_provider))
        # 提示词与工具结果最终发给外部 LLM：出域脱敏（同一出口，见 app.pii.outbound）
        mask_text = None
        if should_mask_outbound(context.settings, provider, True):
            def mask_text(text: str) -> str:
                return mask(text, context.settings.pii_mask_char)
        self.engine = AgentEngine(provider, build_default_registry(), mask_text=mask_text)

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        user_prompt = params.get("user_prompt")
        if not user_prompt:
            raise ValueError("user_prompt 必填")
        if len(str(user_prompt)) > 32_000:
            raise ValueError("user_prompt 超 32k 字符上限")
        return await self.engine.run(
            {
                "system_prompt": params.get("system_prompt"),
                "user_prompt": user_prompt,
                "max_steps": int(params.get("max_steps", 5)),
                "model": params.get("model"),
                "temperature": params.get("temperature"),
            }
        )
