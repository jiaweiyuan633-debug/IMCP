"""智能分块：先按段落、再按句子边界贪婪切分，块间可重叠。

设计要点：
- 语义优先：优先在句子边界收尾，避免把一个句子拦腰截断；
  只有单句超长时才按 ``max_chars`` 硬切。
- 重叠保留上下文：相邻块尾部 ``overlap`` 字符会拼到下一块头部，
  保证检索时跨块的语义片段仍能命中（``overlap >= max_chars`` 时截断为 ``max_chars-1``）。
"""

from __future__ import annotations

import re

# 句子边界：中文句号/问号/感叹号、分号、以及换行（不分段落的单换行也视为边界）
_SENTENCE_SPLIT = re.compile(r"(?<=[。！？!?；;])")
# 段落边界：连续两个及以上换行（空行分隔）
_PARAGRAPH_SPLIT = re.compile(r"\n\s*\n")


def chunk_text(text: str, max_chars: int = 500, overlap: int = 50) -> list[str]:
    """将长文本切成若干块，块间按句子边界重叠。

    流程：
    1. 按空行切成段落，段落内再按句子边界（。！？!?；;\\n）切成句子；
    2. 贪婪拼接句子，直到超过 ``max_chars`` 便落一块；单句超长时硬切为 ``max_chars``；
    3. 每块从上一块尾部截取 ``overlap`` 字符拼到自身头部。
    """
    max_chars = max(max_chars, 1)
    tail = min(max(overlap, 0), max_chars - 1)

    # 1) 切句子
    sentences: list[str] = []
    for paragraph in _PARAGRAPH_SPLIT.split(text):
        sentences.extend(_split_sentences(paragraph))

    # 2) 贪婪拼接
    chunks: list[str] = []
    buf = ""
    for sentence in sentences:
        if not sentence:
            continue
        if buf and len(buf) + len(sentence) <= max_chars:
            buf += sentence
            continue
        if buf:
            chunks.append(buf)
        # 单句超长：硬切成 max_chars 片段，末尾余量作为下一块的起点
        rest = sentence
        while len(rest) > max_chars:
            chunks.append(rest[:max_chars])
            rest = rest[max_chars:]
        buf = rest
    if buf:
        chunks.append(buf)

    return _apply_overlap(chunks, tail)


def chunk_document(title: str, text: str, max_chars: int = 500, overlap: int = 50) -> list[dict]:
    """按 ``chunk_text`` 分块并补充元信息。

    返回 ``[{"title", "content", "chunk_index", "char_count"}]``，
    ``chunk_index`` 从 0 连续递增。
    """
    return [
        {
            "title": title,
            "content": chunk,
            "chunk_index": index,
            "char_count": len(chunk),
        }
        for index, chunk in enumerate(chunk_text(text, max_chars, overlap))
    ]


def _split_sentences(paragraph: str) -> list[str]:
    """把段落按句末标点与换行切成非空句子列表。"""
    sentences: list[str] = []
    for part in _SENTENCE_SPLIT.split(paragraph):
        for sub in part.split("\n"):
            stripped = sub.strip()
            if stripped:
                sentences.append(stripped)
    return sentences


def _apply_overlap(chunks: list[str], tail: int) -> list[str]:
    """把上一块尾部 ``tail`` 字符拼到下一块头部，实现块间重叠。"""
    if tail <= 0 or len(chunks) < 2:
        return chunks
    result = [chunks[0]]
    for i in range(1, len(chunks)):
        prev_tail = result[i - 1][-tail:]
        result.append(prev_tail + chunks[i])
    return result
