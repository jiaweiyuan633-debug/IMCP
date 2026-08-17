"""文档解析任务服务：biz_type=doc_parse。

入参：{"filename": "a.pdf", "content_b64": "<base64>"}
出参：{"pages": [{"page","text"}], "pages_count", "chars"}
"""

from __future__ import annotations

import asyncio
import base64
from typing import Any

from app.rag import UnsupportedFormatError, parse_document
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError

# 批次3（R4-1.49）：单文档解码后字节上限（50MB）——超大文档在事件循环线程池中
# 长时间占用执行线程、放大租约窗口，且拖慢同进程所有请求
MAX_DOCUMENT_BYTES = 50 * 1024 * 1024


class DocParseService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        filename = params.get("filename")
        content_b64 = params.get("content_b64")
        if not filename or not content_b64:
            raise ValueError("filename 与 content_b64 必填")
        # 批次3：base64 解码与文档解析均为 CPU 密集（100 页 PDF 可冻结事件循环数秒），
        # 移入线程池执行；解析前做字节上限与超时裁剪
        content = await asyncio.to_thread(base64.b64decode, content_b64)
        if len(content) > MAX_DOCUMENT_BYTES:
            raise NonRetryableError(
                f"文档超过 {MAX_DOCUMENT_BYTES // 1024 // 1024}MB 上限，请拆分后上传"
            )
        try:
            pages = await asyncio.to_thread(parse_document, filename, content)
        except UnsupportedFormatError as exception:
            raise NonRetryableError(str(exception)) from exception
        return {
            "pages": pages,
            "pages_count": len(pages),
            "chars": sum(len(page["text"]) for page in pages),
        }
