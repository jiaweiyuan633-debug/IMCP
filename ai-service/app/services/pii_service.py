"""PII 检测/脱敏任务服务：biz_type=pii_mask。

入参（文本）：{"text", "kinds"?, "mask_char"?}
入参（结构化数据）：{"data": <任意 JSON>, "mask_char"?}
出参：{"text"/"masked", "detected", "count"}
"""

from __future__ import annotations

import json
from typing import Any

from app.pii import detect, mask, mask_fields
from app.services.base import BaseTaskService
from app.services.context import ServiceContext


class PiiMaskService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        mask_char = params.get("mask_char") or self.context.settings.pii_mask_char
        kinds = params.get("kinds")
        if "data" in params:
            data = params["data"]
            masked = mask_fields(data, mask_char)
            detected = detect(json.dumps(data, ensure_ascii=False))
            return {"masked": masked, "detected": detected, "count": len(detected)}
        text = params.get("text")
        if text is None:
            raise ValueError("text 或 data 至少提供一个")
        detected = detect(text)
        masked = mask(text, mask_char, kinds=kinds)
        return {"text": masked, "detected": detected, "count": len(detected)}
