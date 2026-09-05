"""统一 HTTP 异常处理：稳定错误结构（带 request_id）与 LLM 故障映射。

FastAPI 默认的未捕获异常返回裸 "Internal Server Error"，无 request_id、无法把
链路 ID 透给调用方排障；LLM 上游故障（网络/5xx/超时）默认落到 500，语义也
不对（是上游/网关问题而非本服务内部错误）。此处统一收敛：

- ``LLMError``（可重试的上游故障：网络错误、超时、上游 5xx）→ 502 Bad Gateway；
  消息含 timeout 字样时映射 504 Gateway Timeout；
- ``LLMConfigError``（认证 401/403、模型不存在 404 等配置类上游拒绝）→ 502
  （问题在服务侧对上游的配置，非调用方 payload 错误，不按 4xx 甩给客户端）；
- 其余未捕获异常 → 500，日志记录原始异常，响应只暴露通用信息。

所有错误响应保持同一结构：``{"detail": ..., "error_code": ..., "request_id": ...}``，
request_id 来自请求贯穿上下文（见 app.core.observability）。
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import Request
from fastapi.responses import JSONResponse

from app.core.observability import request_id_var
from app.llm.base import LLMConfigError, LLMError

logger = logging.getLogger(__name__)

_TIMEOUT_HINTS = ("timed out", "timeout", "ReadTimeout", "ConnectTimeout", "read timeout")


def _payload(detail: str, error_code: str, request_id: str | None = None) -> dict[str, Any]:
    return {
        "detail": detail,
        "error_code": error_code,
        "request_id": request_id if request_id is not None else (request_id_var.get() or ""),
    }


async def llm_error_handler(request: Request, exc: LLMError) -> JSONResponse:
    detail = str(exc) or "LLM 上游调用失败"
    status = 504 if any(hint in detail for hint in _TIMEOUT_HINTS) else 502
    return JSONResponse(
        status_code=status,
        content=_payload(detail, "llm_unavailable", request_id_var.get() or ""),
    )


async def llm_config_error_handler(request: Request, exc: LLMConfigError) -> JSONResponse:
    # 认证/模型等上游拒绝属服务侧配置问题：502（网关侧）而非 4xx（客户端入参合法）
    return JSONResponse(
        status_code=502,
        content=_payload(str(exc) or "LLM 配置/认证错误", "llm_config_error", request_id_var.get() or ""),
    )


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    # 不向调用方透出内部异常细节（防信息泄漏），原始异常完整记录到日志
    request_id = request_id_var.get() or ""
    logger.exception("未捕获异常（request_id=%s）: %s", request_id or "-", exc)
    return JSONResponse(
        status_code=500,
        content=_payload("internal server error", "internal_error", request_id),
    )


def register_exception_handlers(app: Any) -> None:
    """把统一异常处理器挂到 FastAPI 应用上。"""
    app.add_exception_handler(LLMError, llm_error_handler)
    app.add_exception_handler(LLMConfigError, llm_config_error_handler)
    app.add_exception_handler(Exception, unhandled_exception_handler)
