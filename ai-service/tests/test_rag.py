"""RAG 深化测试：智能分块、多格式解析、真向量检索管道。

- 分块/解析为纯函数单测；
- 管道用 fakeredis + MockProvider（确定性 16 维 embedding）覆盖入库/检索闭环，
  不连真实 Redis。
"""

import json
import sys
from unittest.mock import MagicMock, patch

import fakeredis.aioredis
import pytest

from app.llm.mock import MockProvider
from app.rag import (
    SUPPORTED_EXTS,
    RagPipeline,
    UnsupportedFormatError,
    chunk_document,
    chunk_text,
    parse_document,
)
from app.vectors.store import RedisVectorStore

# ---------- chunk_text ----------


def test_chunk_text_length_bounds() -> None:
    """所有块长度不超过 max_chars + overlap（重叠允许略超）。"""
    text = (
        "这是第一句。这是第二句！这是第三句？\n\n"
        "这是新段落的第一句。这是新段落的第二句；这是第三句。"
    )
    chunks = chunk_text(text, max_chars=20, overlap=8)
    assert len(chunks) > 1
    for chunk in chunks:
        assert len(chunk) <= 20 + 8, f"块长度超限: {len(chunk)}"


def test_chunk_text_overlap_between_chunks() -> None:
    """多块时相邻块的尾部与头部存在重叠。"""
    text = "第一句。第二句。第三句。第四句。第五句。" * 10
    chunks = chunk_text(text, max_chars=50, overlap=10)
    assert len(chunks) > 1
    for i in range(1, len(chunks)):
        assert chunks[i].startswith(chunks[i - 1][-10:]), f"第 {i} 块缺失重叠"


def test_chunk_text_chinese_sentence_boundary() -> None:
    """中文分块在句号边界收尾，不在句中拦腰截断。"""
    text = "第一句。第二句。第三句。第四句。第五句。第六句。"
    chunks = chunk_text(text, max_chars=18, overlap=0)
    assert len(chunks) > 1
    assert all(chunk.endswith("。") for chunk in chunks)


def test_chunk_text_overlong_sentence_hard_split() -> None:
    """单句超长时硬切为 max_chars 片段，且内容无丢失。"""
    long_sentence = "超" * 100 + "。"
    chunks = chunk_text(long_sentence, max_chars=30, overlap=0)
    assert all(len(chunk) <= 30 for chunk in chunks)
    assert "".join(chunks) == long_sentence


# ---------- chunk_document ----------


def test_chunk_document_structure() -> None:
    """chunk_index 连续、char_count 与内容长度一致、标题透传。"""
    text = "第一句。" * 30
    chunks = chunk_document("测试文档", text, max_chars=40, overlap=5)
    assert len(chunks) > 1
    assert [c["chunk_index"] for c in chunks] == list(range(len(chunks)))
    for c in chunks:
        assert c["title"] == "测试文档"
        assert c["char_count"] == len(c["content"])
        assert c["content"]


# ---------- parse_document ----------


def test_parse_txt_chinese() -> None:
    pages = parse_document("说明.txt", "你好，世界。\n第二行。".encode())
    assert pages == [{"page": 0, "text": "你好，世界。\n第二行。"}]


def test_parse_csv_multiline() -> None:
    content = "name,age\n张三,25\n李四,30\n".encode()
    pages = parse_document("data.csv", content)
    assert pages[0]["page"] == 0
    assert pages[0]["text"].count("\n") >= 2  # 多行内容完整保留


def test_parse_json_text() -> None:
    data = {"title": "测试", "tags": ["a", "b"]}
    content = json.dumps(data, ensure_ascii=False).encode("utf-8")
    pages = parse_document("doc.json", content)
    assert "测试" in pages[0]["text"]


def test_parse_unknown_extension() -> None:
    with pytest.raises(UnsupportedFormatError):
        parse_document("virus.exe", b"whatever")


def test_supported_exts() -> None:
    assert SUPPORTED_EXTS == frozenset({"txt", "md", "csv", "json", "pdf", "docx", "xlsx"})


@pytest.mark.parametrize(
    "filename,module,expect",
    [
        ("a.pdf", "pypdf", "pypdf"),
        ("a.docx", "docx", "python-docx"),
        ("a.xlsx", "openpyxl", "openpyxl"),
    ],
)
def test_parse_lazy_import_missing_dependency(filename, module, expect) -> None:
    """缺依赖时抛 UnsupportedFormatError，而不是裸 ImportError。"""
    with patch.dict(sys.modules, {module: None}), pytest.raises(UnsupportedFormatError, match=expect):
        parse_document(filename, b"data")


class _FakePage:
    def __init__(self, text: str) -> None:
        self._text = text

    def extract_text(self) -> str:
        return self._text


def test_parse_pdf_with_library() -> None:
    """模拟 pypdf：逐页提取、空页剔除、页号正确。"""
    fake = MagicMock()
    fake.PdfReader.return_value = MagicMock(
        pages=[_FakePage("第 1 页内容"), _FakePage(""), _FakePage("第 3 页内容")]
    )
    with patch.dict(sys.modules, {"pypdf": fake}):
        pages = parse_document("手册.pdf", b"%PDF-1.4")
    assert [p["page"] for p in pages] == [1, 3]
    assert pages[0]["text"] == "第 1 页内容"


def test_parse_docx_with_library() -> None:
    """模拟 python-docx：段落拼接。"""
    fake = MagicMock()
    fake.Document.return_value = MagicMock(
        paragraphs=[
            MagicMock(text="第一段"),
            MagicMock(text="第二段"),
        ]
    )
    with patch.dict(sys.modules, {"docx": fake}):
        pages = parse_document("合同.docx", b"PK\x03\x04")
    assert pages == [{"page": 0, "text": "第一段\n第二段"}]


def test_parse_xlsx_with_library() -> None:
    """模拟 openpyxl：非空单元格按行拼接。"""
    fake = MagicMock()
    sheet = MagicMock()
    sheet.iter_rows.return_value = [
        (None, "姓名", "年龄"),
        ("张三", 25, ""),
    ]
    fake.load_workbook.return_value = MagicMock(worksheets=[sheet])
    with patch.dict(sys.modules, {"openpyxl": fake}):
        pages = parse_document("名单.xlsx", b"PK\x03\x04")
    assert pages[0]["page"] == 0
    assert "姓名" in pages[0]["text"]
    assert "张三" in pages[0]["text"]


# ---------- RagPipeline ----------


def test_namespace() -> None:
    assert RagPipeline.namespace(3, 42) == "3:42"


@pytest.mark.asyncio
async def test_ingest_and_retrieve_ranking() -> None:
    """入库 2 篇文档后，语义 query 命中目标文档且 score>0、标题词 query 命中目标篇第一。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    pipeline = RagPipeline(RedisVectorStore(redis), MockProvider())
    docs = [
        {"doc_id": "doc-1", "title": "操作系统", "content": "操作系统负责管理计算机的进程、内存与文件。进程调度是操作系统核心。虚拟内存技术。"},
        {"doc_id": "doc-2", "title": "数据库", "content": "数据库用于持久化存储业务数据。SQL 是查询语言。事务保证一致性。"},
    ]
    result = await pipeline.ingest(1, 100, docs)
    assert result["docs"] == 2
    assert result["chunks"] == 2  # 默认 max_chars 下每篇 1 块

    hits = await pipeline.retrieve(1, 100, "操作系统管理进程和内存")
    assert hits
    assert hits[0]["doc_id"].startswith("doc-1")
    assert hits[0]["score"] > 0
    assert hits[0]["payload"]["doc_id"] == "doc-1"

    # 以 doc-2 标题词查询时，doc-2 排最前
    hits2 = await pipeline.retrieve(1, 100, "数据库")
    assert hits2
    assert hits2[0]["payload"]["doc_id"] == "doc-2"


@pytest.mark.asyncio
async def test_ingest_multi_chunk_counts() -> None:
    """小 max_chars 强制多块，chunks 计数正确且检索仍命中。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    pipeline = RagPipeline(RedisVectorStore(redis), MockProvider())
    long_doc = "操作系统管理进程。" * 40
    result = await pipeline.ingest(2, 3, [{"doc_id": "long", "title": "长文", "content": long_doc}], max_chars=20, overlap=4)
    assert result["docs"] == 1
    assert result["chunks"] > 1

    hits = await pipeline.retrieve(2, 3, "操作系统")
    assert hits
    assert hits[0]["payload"]["doc_id"] == "long"


@pytest.mark.asyncio
async def test_retrieve_empty_store() -> None:
    """空库检索返回空列表。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    pipeline = RagPipeline(RedisVectorStore(redis), MockProvider())
    assert await pipeline.retrieve(1, 100, "任意查询") == []


@pytest.mark.asyncio
async def test_retrieve_empty_query() -> None:
    """空 query 向量为空，返回空列表。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    pipeline = RagPipeline(RedisVectorStore(redis), MockProvider())
    await pipeline.ingest(1, 100, [{"doc_id": "d", "title": "t", "content": "内容。"}])
    assert await pipeline.retrieve(1, 100, "") == []


@pytest.mark.asyncio
async def test_tenant_namespace_isolation() -> None:
    """不同 tenant 的库互不可见。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    pipeline = RagPipeline(RedisVectorStore(redis), MockProvider())
    await pipeline.ingest(1, 100, [{"doc_id": "d", "title": "t", "content": "操作系统管理进程。"}])
    assert await pipeline.retrieve(2, 100, "操作系统") == []
    assert await pipeline.retrieve(1, 100, "操作系统")
