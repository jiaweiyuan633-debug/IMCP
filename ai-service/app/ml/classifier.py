"""KNN 文本分类器：TF-IDF 稀疏向量 + 余弦相似度，支持序列化与 Redis 持久化。

纯标准库实现；训练数据按标签原样保存在模型内，predict 时取 k 个最近邻做多数投票。
"""

from __future__ import annotations

import json
from collections import Counter
from typing import Self

from app.ml.tfidf import TfidfVectorizer, cosine


class KNNClassifier:
    """基于 TF-IDF + 余弦相似度的最近邻分类器。"""

    def __init__(self) -> None:
        self.vectorizer = TfidfVectorizer()
        self.labels: list[str] = []
        self.samples: list[dict[int, float]] = []

    def fit(self, labels: list[str], docs: list[str]) -> Self:
        """用平行标签与文档列表训练；labels 与 docs 必须等长。"""
        if len(labels) != len(docs):
            raise ValueError("labels 与 docs 长度必须一致")
        self.vectorizer.fit(docs)
        self.labels = list(labels)
        self.samples = [self.vectorizer.transform(doc) for doc in docs]
        return self

    def predict(self, doc: str, k: int = 3) -> dict:
        """对单个文本预测：返回多数票标签、置信度（多数票/k）与最近邻明细。

        k 超过样本数时截断为样本数；空文档退化为取前 k 条样本做多数投票，不抛异常。
        """
        if not self.samples:
            return {"label": None, "confidence": 0.0, "neighbors": []}
        query = self.vectorizer.transform(doc)
        k = max(1, min(k, len(self.samples)))
        scored = sorted(
            ((cosine(query, sample), i) for i, sample in enumerate(self.samples)),
            key=lambda item: item[0],
            reverse=True,
        )
        top = scored[:k]
        neighbors = [
            {"label": self.labels[i], "similarity": round(sim, 6)} for sim, i in top
        ]
        votes = Counter(self.labels[i] for _, i in top)
        label, count = votes.most_common(1)[0]
        return {"label": label, "confidence": count / k, "neighbors": neighbors}

    def to_json(self) -> dict:
        """序列化为可 JSON 化的 dict，from_json 可完整还原。"""
        return {
            "labels": self.labels,
            "samples": self.samples,
            "vectorizer": {
                "vocabulary": self.vectorizer.vocabulary,
                "idf": self.vectorizer.idf,
                "n_docs": self.vectorizer.n_docs,
            },
        }

    @classmethod
    def from_json(cls, data: dict) -> KNNClassifier:
        """从 to_json 的 dict 还原模型。"""
        obj = cls()
        obj.labels = list(data["labels"])
        # JSON 键为字符串，需还原为 int 稀疏下标
        obj.samples = [
            {int(str_key): float(val) for str_key, val in sample.items()}
            for sample in data["samples"]
        ]
        obj.vectorizer = TfidfVectorizer()
        obj.vectorizer.vocabulary = dict(data["vectorizer"]["vocabulary"])
        obj.vectorizer.idf = list(data["vectorizer"]["idf"])
        obj.vectorizer.n_docs = int(data["vectorizer"]["n_docs"])
        return obj


async def train_model(redis, name: str, labels: list[str], docs: list[str]) -> dict:
    """训练 KNN 模型并序列化持久化到 Redis key ``ai:ml:model:{name}``。"""
    model = KNNClassifier().fit(labels, docs)
    payload = json.dumps(model.to_json(), ensure_ascii=False)
    await redis.set(f"ai:ml:model:{name}", payload)
    return {
        "name": name,
        "label_count": len(set(labels)),
        "vocab_size": len(model.vectorizer.vocabulary),
        "samples": len(docs),
    }


async def load_model(redis, name: str) -> KNNClassifier | None:
    """从 Redis 读取模型；不存在时返回 None。"""
    raw = await redis.get(f"ai:ml:model:{name}")
    if not raw:
        return None
    return KNNClassifier.from_json(json.loads(raw))
