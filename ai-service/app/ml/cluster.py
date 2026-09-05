"""KMeans 文本聚类：TF-IDF → 归一化稠密向量 → 欧氏距离 KMeans。

纯标准库实现；质心用 ``random.Random(seed)`` 初始化，保证相同输入与 seed 结果完全确定。
"""

from __future__ import annotations

import math
import random

from app.ml.tfidf import TfidfVectorizer
from app.vectors.linalg import normalize


def _dense(sparse: dict[int, float], size: int) -> list[float]:
    """稀疏向量扩展为稠密向量（缺失维度补 0）。"""
    vec = [0.0] * size
    for idx, val in sparse.items():
        vec[idx] = val
    return vec


def _euclidean(a: list[float], b: list[float]) -> float:
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


class KMeansTextClusterer:
    """轻量 KMeans 文本聚类器。"""

    def cluster(self, docs: list[str], k: int = 2, max_iter: int = 20, seed: int = 0) -> dict:
        """对文档集聚类，返回簇详情、惯量（簇内距离平方和）与迭代次数。

        k 超过文档数时抛 ValueError。
        """
        n = len(docs)
        if k > n:
            raise ValueError(f"k={k} 超过文档数 {n}")
        if k < 1:
            raise ValueError("k 必须 >= 1")

        # 1. TF-IDF 稀疏向量 → 归一化稠密向量
        vectorizer = TfidfVectorizer().fit(docs)
        size = len(vectorizer.vocabulary)
        vectors = [normalize(_dense(vectorizer.transform(doc), size)) for doc in docs]

        # 2. KMeans：固定 seed 采样 k 个文档作为初始质心
        rng = random.Random(seed)
        centroids = [vectors[i] for i in rng.sample(range(n), k)]
        assignments: list[int] = []
        iterations = 0
        for iteration in range(1, max_iter + 1):
            iterations = iteration
            new_assign = [
                min(range(k), key=lambda c: _euclidean(vec, centroids[c])) for vec in vectors
            ]
            if assignments and new_assign == assignments:
                break
            assignments = new_assign
            # 3. 重算质心；空簇保留旧质心（保证确定性）
            for c in range(k):
                members = [vectors[i] for i in range(n) if assignments[i] == c]
                if members:
                    centroids[c] = [sum(col) / len(col) for col in zip(*members)]

        # 4. 汇总结果
        clusters = []
        inertia = 0.0
        for c in range(k):
            indices = [i for i in range(n) if assignments[i] == c]
            for i in indices:
                inertia += _euclidean(vectors[i], centroids[c]) ** 2
            clusters.append(
                {
                    "label": c,
                    "indices": indices,
                    "centroid": [round(x, 6) for x in centroids[c]],
                }
            )
        return {"clusters": clusters, "inertia": round(inertia, 6), "iterations": iterations}
