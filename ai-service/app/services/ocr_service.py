"""OCR 任务服务：biz_type=ocr。

入参：{"image_b64": "<base64>", "lang"?}
出参：{"text", "provider", "chars"}
"""

from __future__ import annotations

import base64
from typing import Any

from app.ocr import OCRDependencyError, get_ocr_provider
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError


class OcrService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context
        self.provider = get_ocr_provider(context.settings)

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        image_b64 = params.get("image_b64")
        if not image_b64:
            raise ValueError("image_b64 必填")
        try:
            image_bytes = base64.b64decode(image_b64)
            text = await self.provider.recognize(image_bytes, lang=params.get("lang"))
        except OCRDependencyError as exception:
            raise NonRetryableError(str(exception)) from exception
        return {"text": text, "provider": type(self.provider).__name__, "chars": len(text)}
