"""纯 Python 向量运算：点积 / 范数 / 余弦相似度 / 归一化。

刻意不引入 numpy：本服务为 scaffold 规模（数百到数千向量），纯 Python 足够且零重依赖，
Docker 镜像保持精简。大规模场景应切换 Milvus / pgvector / Qdrant。
"""

from __future__ import annotations

import math
from typing import Iterable


def dot(a: Iterable[float], b: Iterable[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


def norm(vector: Iterable[float]) -> float:
    return math.sqrt(sum(x * x for x in vector))


def cosine(a: Iterable[float], b: Iterable[float]) -> float:
    """余弦相似度；任一侧为零向量时返回 0（无相似性而非 NaN）。"""
    product = dot(a, b)
    left, right = norm(a), norm(b)
    if left == 0.0 or right == 0.0:
        return 0.0
    return product / (left * right)


def normalize(vector: list[float]) -> list[float]:
    length = norm(vector)
    if length == 0.0:
        return vector
    return [x / length for x in vector]
