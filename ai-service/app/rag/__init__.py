"""RAG 深化：智能分块 + 多格式文档解析 + 真向量检索管道。"""

from app.rag.chunker import chunk_document, chunk_text
from app.rag.parser import SUPPORTED_EXTS, UnsupportedFormatError, parse_document
from app.rag.pipeline import RagPipeline

__all__ = [
    "SUPPORTED_EXTS",
    "RagPipeline",
    "UnsupportedFormatError",
    "chunk_document",
    "chunk_text",
    "parse_document",
]
