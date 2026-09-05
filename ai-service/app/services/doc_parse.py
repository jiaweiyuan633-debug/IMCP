"""文档解析任务服务：biz_type=doc_parse。

入参：{"filename": "a.pdf", "content_b64": "<base64>"}
出参：{"pages": [{"page","text"}], "pages_count", "chars"}
"""

from __future__ import annotations

import base64
from typing import Any

from app.core.threads import run_cpu
from app.rag import UnsupportedFormatError, parse_document
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError

# 单文档解码后字节上限（50MB）——超大文档长时间占用有界 CPU 线程、放大租约窗口
MAX_DOCUMENT_BYTES = 50 * 1024 * 1024
# base64 长度上限（对 MAX_DOCUMENT_BYTES 的编码上界，按 4/3 扩容并向上取整）：
# 先于解码预估长度、超限直接拒绝，避免为超大 payload 白白解码
MAX_ENCODED_LENGTH = ((MAX_DOCUMENT_BYTES + 2) // 3) * 4


class DocParseService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        filename = params.get("filename")
        content_b64 = params.get("content_b64")
        if not filename or not content_b64:
            raise ValueError("filename 与 content_b64 必填")
        # 解码前按编码长度预估拒绝超大输入（base64 解码与解析均为 CPU 密集且线程内
        # 不可中断，只能以输入规模预检 + 有界线程池控制占用）
        if len(str(content_b64)) > MAX_ENCODED_LENGTH:
            raise NonRetryableError(
                f"文档超过 {MAX_DOCUMENT_BYTES // 1024 // 1024}MB 上限，请拆分后上传"
            )
        content = await run_cpu(base64.b64decode, content_b64)
        if len(content) > MAX_DOCUMENT_BYTES:
            raise NonRetryableError(
                f"文档超过 {MAX_DOCUMENT_BYTES // 1024 // 1024}MB 上限，请拆分后上传"
            )
        try:
            pages = await run_cpu(parse_document, filename, content)
        except UnsupportedFormatError as exception:
            raise NonRetryableError(str(exception)) from exception
        return {
            "pages": pages,
            "pages_count": len(pages),
            "chars": sum(len(page["text"]) for page in pages),
        }
