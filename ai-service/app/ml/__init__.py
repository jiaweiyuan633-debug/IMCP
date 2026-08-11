"""轻量 ML 能力模块：纯 Python 实现的 TF-IDF / KNN 分类 / KMeans 聚类。

仅依赖标准库（math / re / random / collections）与 app.vectors.linalg，
不引入 numpy / sklearn，保持镜像精简。
"""

from app.ml.classifier import KNNClassifier, load_model, train_model
from app.ml.cluster import KMeansTextClusterer
from app.ml.tfidf import TfidfVectorizer, tokenize

__all__ = [
    "KNNClassifier",
    "TfidfVectorizer",
    "KMeansTextClusterer",
    "train_model",
    "load_model",
    "tokenize",
]
