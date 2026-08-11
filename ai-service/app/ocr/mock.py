"""确定性 Mock OCR：不依赖任何外部工具，开箱即用、测试可重复。

行为约定（纯函数式确定性，同输入必得同输出）：
- 从图片字节中提取最长的连续可打印 ASCII 序列（字母数字与常见标点）作为识别结果；
- 若字节中不含任何可打印 ASCII 序列，返回确定性指纹 ``OCR(mock): <sha256 前 8 位>``。
"""

from __future__ import annotations

import hashlib
import re


class MockOCRProvider:
    """Mock OCR 实现：从原始字节直接"识别"，避免引入真实 OCR 依赖。"""

    # 可打印 ASCII：字母数字与常见标点（含空格）
    _PRINTABLE = re.compile(rb"[A-Za-z0-9 ,.!?;:'\"()\-+=/]+")

    async def recognize(self, image_bytes: bytes, lang: str | None = None) -> str:
        """提取最长可打印 ASCII 序列返回；无匹配则返回确定性指纹。"""
        matches = self._PRINTABLE.findall(image_bytes)
        if matches:
            return max(matches, key=len).decode("ascii", errors="ignore").strip()
        digest = hashlib.sha256(image_bytes).hexdigest()[:8]
        return f"OCR(mock): {digest}"
