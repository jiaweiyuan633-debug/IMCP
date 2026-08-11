"""文档解析任务服务：biz_type=doc_parse。

入参：{"filename": "a.pdf", "content_b64": "<base64>"}
出参：{"pages": [{"page","text"}], "pages_count", "chars"}
"""

from __future__ import annotations

import base64
from typing import Any

from app.rag import UnsupportedFormatError, parse_document
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError


class DocParseService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        filename = params.get("filename")
        content_b64 = params.get("content_b64")
        if not filename or not content_b64:
            raise ValueError("filename 与 content_b64 必填")
        try:
            content = base64.b64decode(content_b64)
            pages = parse_document(filename, content)
        except UnsupportedFormatError as exception:
            raise NonRetryableError(str(exception)) from exception
        return {
            "pages": pages,
            "pages_count": len(pages),
            "chars": sum(len(page["text"]) for page in pages),
        }
