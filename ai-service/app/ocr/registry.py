"""OCR 提供方选择：按配置解析实现，tesseract 不可用时回退 Mock。

选择规则：
- ``settings.ocr_provider == "tesseract"`` 且 ``TesseractOCRProvider.is_available()``
  为 True 时返回真实 Tesseract 实例（构造失败同样回退 Mock）；
- 其余情况（默认 mock / tesseract 不可用）返回确定性 MockOCRProvider。
"""

from __future__ import annotations

from app.core.config import Settings
from app.ocr.base import OCRProvider
from app.ocr.mock import MockOCRProvider
from app.ocr.tesseract import TesseractOCRProvider


def get_ocr_provider(settings: Settings) -> OCRProvider:
    """按配置选择 OCR 实现；探测失败或构造失败均回退 Mock（不抛异常）。"""
    if settings.ocr_provider == "tesseract" and TesseractOCRProvider.is_available():
        try:
            return TesseractOCRProvider()
        except Exception:  # noqa: BLE001 构造失败回退 Mock，不向上抛
            return MockOCRProvider()
    return MockOCRProvider()
