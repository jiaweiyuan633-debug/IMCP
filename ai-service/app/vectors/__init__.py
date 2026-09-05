"""向量层：纯 Python 余弦计算 + Redis 精确向量检索（真向量，非倒排/关键词）。"""

from app.vectors.linalg import cosine, dot, norm, normalize
from app.vectors.store import RedisVectorStore, VectorDimensionError

__all__ = [
    "RedisVectorStore",
    "VectorDimensionError",
    "cosine",
    "dot",
    "norm",
    "normalize",
]
