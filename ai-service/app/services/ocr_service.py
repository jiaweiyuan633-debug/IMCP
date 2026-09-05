"""OCR 任务服务：biz_type=ocr。

入参：{"image_b64": "<base64>", "lang"?}
出参：{"text", "provider", "chars"}
"""

from __future__ import annotations

import base64
from typing import Any

from app.core.threads import run_cpu
from app.ocr import OCRDependencyError, get_ocr_provider
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError

# 单张图片解码后字节上限（20MB）：超大图片在 OCR 引擎里长时间占用有界线程
MAX_IMAGE_BYTES = 20 * 1024 * 1024
# base64 编码长度上限（对 MAX_IMAGE_BYTES 的 4/3 上界），解码前预估拒绝
MAX_IMAGE_ENCODED_LENGTH = ((MAX_IMAGE_BYTES + 2) // 3) * 4


class OcrService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context
        self.provider = get_ocr_provider(context.settings)

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        image_b64 = params.get("image_b64")
        if not image_b64:
            raise ValueError("image_b64 必填")
        # 解码前预估长度拒绝超大输入（解码与识别均 CPU 密集、线程内不可中断）
        if len(str(image_b64)) > MAX_IMAGE_ENCODED_LENGTH:
            raise NonRetryableError(
                f"图片超过 {MAX_IMAGE_BYTES // 1024 // 1024}MB 上限，请压缩后上传"
            )
        try:
            image_bytes = await run_cpu(base64.b64decode, image_b64)
            if len(image_bytes) > MAX_IMAGE_BYTES:
                raise NonRetryableError(
                    f"图片超过 {MAX_IMAGE_BYTES // 1024 // 1024}MB 上限，请压缩后上传"
                )
            text = await self.provider.recognize(image_bytes, lang=params.get("lang"))
        except OCRDependencyError as exception:
            raise NonRetryableError(str(exception)) from exception
        return {"text": text, "provider": type(self.provider).__name__, "chars": len(text)}
