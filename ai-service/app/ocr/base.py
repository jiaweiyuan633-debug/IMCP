"""OCR 提供方抽象：图片字节 → 识别文本。

实现约定：
- 输入统一为图片原始字节（PNG/JPEG 等），输出为识别出的文本字符串。
- ``recognize`` 均为 async，调用方统一 await；lang 为 None 时由实现决定默认语言。
- 依赖缺失/不可用统一抛 ``OCRDependencyError``（构造期探测失败即抛）。
"""

from __future__ import annotations

from typing import Protocol


class OCRProvider(Protocol):
    """统一的 OCR 提供方接口（真实实现与测试 Mock 均实现本协议）。"""

    async def recognize(self, image_bytes: bytes, lang: str | None = None) -> str:
        """从图片字节识别文本；lang 为 None 时使用提供方默认语言。"""


class OCRDependencyError(RuntimeError):
    """依赖缺失/不可用（如 tesseract 未安装或不可执行）时抛出。"""
