"""真实 Tesseract OCR：基于 pytesseract + Pillow，需本机安装 tesseract 可执行文件。

- ``is_available()`` 静态探测依赖是否齐备，任何异常都不抛出、返回 False；
- ``recognize()`` 把 CPU 密集的 image_to_string 放到有界 CPU 线程池（见
  app.core.threads.run_cpu）执行，避免阻塞事件循环且并发受控；
- 依赖（pytesseract/PIL）在方法内部延迟导入：模块可被安全导入，探测失败走 Mock 回退。
"""

from __future__ import annotations

import io
from shutil import which

from app.core.threads import run_cpu
from app.ocr.base import OCRDependencyError


class TesseractOCRProvider:
    """调用本机 tesseract 可执行文件做真实 OCR。"""

    @classmethod
    def is_available(cls) -> bool:
        """探测依赖是否可用：能 import pytesseract/PIL 且能定位 tesseract 可执行文件。"""
        try:
            import PIL  # noqa: F401
            import pytesseract  # noqa: F401

            return which("tesseract") is not None
        except Exception:  # noqa: BLE001 探测阶段吞掉所有异常，返回 False
            return False

    def __init__(self) -> None:
        if not self.is_available():
            raise OCRDependencyError("tesseract 依赖不可用")

    async def recognize(self, image_bytes: bytes, lang: str | None = None) -> str:
        """在有界 CPU 线程池中调用 pytesseract 识别；lang 为 None 时省略语言参数。"""

        def _run() -> str:
            import pytesseract
            from PIL import Image

            image = Image.open(io.BytesIO(image_bytes))
            if lang is None:
                return pytesseract.image_to_string(image)
            return pytesseract.image_to_string(image, lang=lang)

        return await run_cpu(_run)
