"""OCR 提供方选择：按配置解析实现，tesseract 不可用时回退 Mock（并告警）。

选择规则：
- ``settings.ocr_provider == "tesseract"`` 且 ``TesseractOCRProvider.is_available()``
  为 True 时返回真实 Tesseract 实例（构造失败同样回退 Mock）；
- 其余情况（默认 mock / tesseract 不可用）返回确定性 MockOCRProvider。

批次3（R4-1.49）：tesseract 探测/构造失败回退 Mock 时**必须告警**——此前静默回退，
生产配置了 tesseract 却拿到假 OCR 结果且无任何日志（"无声数据造假"）；新增
``settings.ocr_fail_fast``，置 true 时失败直接抛异常（不静默降级）。
"""

from __future__ import annotations

import logging

from app.core.config import Settings
from app.ocr.base import OCRProvider
from app.ocr.mock import MockOCRProvider
from app.ocr.tesseract import TesseractOCRProvider

logger = logging.getLogger(__name__)


def get_ocr_provider(settings: Settings) -> OCRProvider:
    """按配置选择 OCR 实现；探测失败或构造失败回退 Mock 并告警（可 fail-fast）。"""
    if settings.ocr_provider == "tesseract":
        if not TesseractOCRProvider.is_available():
            if settings.ocr_fail_fast:
                raise RuntimeError("OCR_PROVIDER=tesseract 但 Tesseract 不可用（fail_fast 开启）")
            logger.warning("OCR_PROVIDER=tesseract 但 Tesseract 不可用，回退 Mock 提供方")
            return MockOCRProvider()
        try:
            return TesseractOCRProvider()
        except Exception as exception:  # 构造失败回退 Mock（或 fail-fast）
            if settings.ocr_fail_fast:
                raise RuntimeError(f"OCR_PROVIDER=tesseract 初始化失败（fail_fast 开启）: {exception}") from exception
            logger.warning("OCR_PROVIDER=tesseract 初始化失败，回退 Mock 提供方: %s", exception)
            return MockOCRProvider()
    return MockOCRProvider()
