"""PII 检测与脱敏实现。

提供三个入口：
- ``detect``：识别文本中的 PII 命中区间（合并重叠后按 start 排序）。
- ``mask``：把命中区间替换为指定字符，支持按类型过滤。
- ``mask_fields``：递归脱敏 dict / list / str，敏感键（password 等）整体打码。
"""

from collections.abc import Iterable
from typing import Any

from app.pii.rules import ALL_KINDS, PII_RULES

# 敏感键集合（大小写不敏感）：其对应值整体替换为 mask_char * 8，不透出原文。
SENSITIVE_KEYS = ("password", "token", "secret", "apikey", "authorization", "credentials")


def detect(text: str) -> list[dict]:
    """遍历 PII_RULES 收集所有匹配，合并重叠后按 start 排序。

    合并规则：同一 start 区间保留更长者；完全同区间保留优先级更高
    （更靠前的规则）者；跨 start 重叠时优先保留更靠前的区间。
    返回 [{"kind", "start", "end", "value"}]，区间互不重叠。
    """
    found: list[tuple[int, int, int, str, str]] = []
    for priority, rule in enumerate(PII_RULES):
        for m in rule.pattern.finditer(text):
            found.append((priority, m.start(), m.end(), rule.kind, m.group(0)))
    # 排序：按 start 升序；同 start 按长度降序；完全同区间按优先级升序。
    found.sort(key=lambda item: (item[1], -item[2], item[0]))
    merged: list[tuple[int, int, int, str, str]] = []
    for item in found:
        if not merged or item[1] >= merged[-1][2]:
            merged.append(item)
    return [
        {"kind": kind, "start": start, "end": end, "value": value}
        for _, start, end, kind, value in merged
    ]


def mask(text: str, mask_char: str = "*", kinds: Iterable[str] | None = None) -> str:
    """把命中区间替换为 mask_char 重复区间长度次。

    kinds 为 None 时脱敏全部类型，否则只脱敏指定的类型。
    未命中的文本原样保留。
    """
    selected = tuple(ALL_KINDS) if kinds is None else tuple(kinds)
    spans = [s for s in detect(text) if s["kind"] in selected]
    if not spans:
        return text
    parts: list[str] = []
    cursor = 0
    for span in spans:
        parts.append(text[cursor : span["start"]])
        parts.append(mask_char * (span["end"] - span["start"]))
        cursor = span["end"]
    parts.append(text[cursor:])
    return "".join(parts)


def mask_fields(data: Any, mask_char: str = "*") -> Any:
    """递归脱敏数据，返回与原结构一致的新结构。

    - dict：对每个值递归；若键属于敏感键集合（大小写不敏感），其值整体
      替换为 mask_char * 8（不透出原文，也不进入常规 detect）。
    - list：对每个元素递归。
    - str：走 mask 常规脱敏。
    - 其他类型：原样返回。
    """
    if isinstance(data, dict):
        result: dict[Any, Any] = {}
        for key, value in data.items():
            if isinstance(key, str) and key.lower() in SENSITIVE_KEYS:
                result[key] = mask_char * 8
            else:
                result[key] = mask_fields(value, mask_char)
        return result
    if isinstance(data, list):
        return [mask_fields(item, mask_char) for item in data]
    if isinstance(data, str):
        return mask(data, mask_char)
    return data
