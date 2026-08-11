import pytest
from fakeredis.aioredis import FakeRedis

from app.ml import KMeansTextClusterer, KNNClassifier, TfidfVectorizer, load_model, tokenize, train_model
from app.ml.tfidf import cosine


# ---------- tokenize ----------

def test_tokenize_cjk_bigram() -> None:
    assert tokenize("你好世界") == ["你好", "好世", "世界"]
    assert tokenize("中") == ["中"]


def test_tokenize_ascii_word() -> None:
    assert tokenize("Hello, World! foo123") == ["hello", "world", "foo123"]


def test_tokenize_mixed() -> None:
    assert tokenize("AI 智能管理 平台") == ["ai", "智能", "能管", "管理", "平台"]


def test_tokenize_empty() -> None:
    assert tokenize("") == []


# ---------- TfidfVectorizer / cosine ----------

def test_tfidf_vocabulary_and_cosine() -> None:
    vec = TfidfVectorizer().fit(["你好 世界", "你好 世界 世界"])
    # 首次出现顺序：你好 / 世界
    assert list(vec.vocabulary) == ["你好", "世界"]
    assert vec.n_docs == 2
    assert vec.idf[0] == pytest.approx(1.0)
    a = vec.transform("你好")
    b = vec.transform("你好 世界")
    assert set(a) == {0}
    assert set(b) == {0, 1}
    assert cosine(a, a) == pytest.approx(1.0)
    assert cosine(a, {}) == 0.0
    assert cosine({}, {}) == 0.0


# ---------- KNN 分类 ----------

SPORT = ["足球 篮球 比赛 球队", "篮球 运动 比赛 得分"]
TECH = ["芯片 人工智能 编程 软件", "科技 互联网 软件 编程"]
FIN = ["股票 基金 投资 财经", "金融 银行 股市 投资"]


def test_knn_three_class() -> None:
    model = KNNClassifier().fit(
        labels=["体育", "体育", "科技", "科技", "财经", "财经"],
        docs=SPORT + TECH + FIN,
    )
    for doc, expected in [("足球 比赛 球队", "体育"), ("编程 软件 芯片", "科技"), ("股票 基金 投资", "财经")]:
        result = model.predict(doc)
        assert result["label"] == expected
        assert result["confidence"] > 0
        assert len(result["neighbors"]) == 3


def test_knn_k_truncated() -> None:
    model = KNNClassifier().fit(["a", "b"], ["x y", "p q"])
    result = model.predict("x", k=10)
    assert result["label"] in ("a", "b")
    assert len(result["neighbors"]) == 2


def test_knn_json_roundtrip() -> None:
    model = KNNClassifier().fit(labels=["体育", "科技", "财经"], docs=["足球 比赛", "芯片 软件", "股票 基金"])
    restored = KNNClassifier.from_json(model.to_json())
    assert restored.predict("足球") == model.predict("足球")
    assert restored.vectorizer.vocabulary == model.vectorizer.vocabulary


def test_knn_empty_doc() -> None:
    model = KNNClassifier().fit(["a", "b"], ["x y", "p q"])
    result = model.predict("")
    assert result["label"] in ("a", "b")
    assert result["confidence"] > 0


@pytest.mark.asyncio
async def test_train_load_redis() -> None:
    redis = FakeRedis(decode_responses=True)
    info = await train_model(redis, "demo", ["a", "a", "b", "b"], ["x y", "x z", "p q", "p r"])
    assert info == {"name": "demo", "label_count": 2, "vocab_size": 6, "samples": 4}
    model = await load_model(redis, "demo")
    assert model is not None
    assert model.predict("x")["label"] == "a"
    assert await load_model(redis, "missing") is None


# ---------- KMeans 聚类 ----------

def test_kmeans_deterministic_two_clusters() -> None:
    docs = ["苹果 香蕉 水果", "香蕉 苹果 水果", "股票 基金 投资", "财经 股票 基金"]
    first = KMeansTextClusterer().cluster(docs, k=2, seed=0)
    second = KMeansTextClusterer().cluster(docs, k=2, seed=0)
    assert first == second

    sets = [set(cluster["indices"]) for cluster in first["clusters"]]
    assert sorted(i for s in sets for i in s) == [0, 1, 2, 3]
    assert all(s for s in sets)
    # 水果组 {0,1} 与财经组 {2,3} 应各自落在同一簇
    assert {0, 1} in sets and {2, 3} in sets
    assert all(len(cluster["centroid"]) > 0 for cluster in first["clusters"])
    assert first["inertia"] > 0
    assert 1 <= first["iterations"] <= 20


def test_kmeans_k_too_large() -> None:
    with pytest.raises(ValueError):
        KMeansTextClusterer().cluster(["only"], k=2)
