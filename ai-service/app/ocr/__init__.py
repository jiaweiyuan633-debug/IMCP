"""OCR 层：可插拔的图片文字识别（确定性 Mock 兜底 / Tesseract 真实识别）。"""

from app.ocr.base import OCRDependencyError, OCRProvider
from app.ocr.mock import MockOCRProvider
from app.ocr.registry import get_ocr_provider
from app.ocr.tesseract import TesseractOCRProvider

__all__ = [
    "MockOCRProvider",
    "OCRDependencyError",
    "OCRProvider",
    "TesseractOCRProvider",
    "get_ocr_provider",
]
