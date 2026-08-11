"""纯 Python TF-IDF 向量化：中文分词为字 bigram，英文按词切分。

刻意不引入 sklearn/numpy：本服务为 scaffold 规模，标准库足以支撑轻量分类/聚类，
Docker 镜像保持精简。产出稀疏向量 dict[int, float]，term_index 为 vocabulary 下标。
"""

from __future__ import annotations

import math
import re
from typing import Self

# CJK 统一表意文字 / 扩展 A / 兼容表意文字
_CJK_RE = re.compile(r"[一-鿿㐀-䶿豈-﫿]")


def tokenize(text: str) -> list[str]:
    """小写化后切分：ASCII 词按非字母数字切分；CJK 连续段生成相邻两字 bigram。

    规则：
    - ASCII 字母数字连续段作为整体词。
    - CJK 连续段长度为 n 时产出 n-1 个相邻两字 bigram；段长为 1 时退化为该字 unigram。
    - 其它字符（标点、空白等）作为分隔符。
    """
    text = text.lower()
    tokens: list[str] = []
    ascii_word: list[str] = []
    cjk_run: list[str] = []

    def flush_ascii() -> None:
        if ascii_word:
            tokens.append("".join(ascii_word))
            ascii_word.clear()

    def flush_cjk() -> None:
        if cjk_run:
            run = "".join(cjk_run)
            if len(run) == 1:
                tokens.append(run)
            else:
                tokens.extend(run[i : i + 2] for i in range(len(run) - 1))
            cjk_run.clear()

    for ch in text:
        if ch.isascii() and ch.isalnum():
            flush_cjk()
            ascii_word.append(ch)
        elif _CJK_RE.match(ch):
            flush_ascii()
            cjk_run.append(ch)
        else:
            flush_ascii()
            flush_cjk()
    flush_ascii()
    flush_cjk()
    return tokens


class TfidfVectorizer:
    """基于字 bigram 的 TF-IDF 向量化器。

    vocabulary 按首次出现顺序构建，idf 与该顺序对齐：
    idf(t) = log((1 + n) / (1 + df(t))) + 1
    """

    def __init__(self) -> None:
        self.vocabulary: dict[str, int] = {}
        self.idf: list[float] = []
        self.n_docs: int = 0

    def fit(self, docs: list[str]) -> Self:
        """统计文档集构建 vocabulary 与 idf，返回自身以便链式调用。"""
        self.vocabulary = {}
        df: dict[str, int] = {}
        for doc in docs:
            # dict.fromkeys 保序去重，避免 set 受 hash 随机化影响迭代顺序
            for term in dict.fromkeys(tokenize(doc)):
                if term not in self.vocabulary:
                    self.vocabulary[term] = len(self.vocabulary)
                df[term] = df.get(term, 0) + 1
        self.n_docs = len(docs)
        n = self.n_docs
        self.idf = [math.log((1 + n) / (1 + df[term])) + 1 for term in self.vocabulary]
        return self

    def transform(self, doc: str) -> dict[int, float]:
        """单文档 → 稀疏 TF-IDF 向量；未出现在 vocabulary 中的词被忽略。"""
        weights: dict[int, float] = {}
        for term in tokenize(doc):
            idx = self.vocabulary.get(term)
            if idx is None:
                continue
            weights[idx] = weights.get(idx, 0) + 1
        for idx, tf in weights.items():
            weights[idx] = tf * self.idf[idx]
        return weights


def cosine(a: dict[int, float], b: dict[int, float]) -> float:
    """稀疏余弦相似度；任一侧为零向量时返回 0（无相似性而非 NaN）。"""
    if not a or not b:
        return 0.0
    product = sum(av * b.get(idx, 0.0) for idx, av in a.items())
    left = math.sqrt(sum(v * v for v in a.values()))
    right = math.sqrt(sum(v * v for v in b.values()))
    if left == 0.0 or right == 0.0:
        return 0.0
    return product / (left * right)
