"""统一日志与跨服务 request_id 贯穿。

后端（Java）以 X-Request-Id 头贯穿全链路：RequestIdFilter 读取/生成请求 ID，
写入 MDC（%X{requestId}）并在出站调用时转发给 AI 服务（AiPythonClient/LlmChatClient）。
AI 服务此前只在任务记录里保存 request_id，自身日志与访问日志均无关联，
跨服务排障时两端日志无法串联。此处补齐三块：

- request_id ContextVar：请求中途沿用调用方 X-Request-Id（无则生成 uuid4 hex），
  响应回写，供两端对齐；
- RequestIdFormatter：每条日志记录注入当前请求 ID（无请求归属回退 '-'），
  与后端日志格式对齐（时间 级别 [requestId] logger - 消息）；
- RequestLogMiddleware：每个请求一行访问日志（方法/路径/状态/耗时/客户端）。

后台任务关联：工作协程由提交任务的请求上下文派生（asyncio.create_task 复制
上下文），故 worker 逐轮清空 request_id、执行单个任务时从任务记录恢复
（见 app.tasks.manager），保证任务执行日志携带提交它的请求 ID，而非建队请求的 ID。
"""

import logging
import logging.config
import time
from collections.abc import Awaitable, Callable
from contextvars import ContextVar
from uuid import uuid4

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

REQUEST_ID_HEADER = "X-Request-Id"

# 默认空串：无请求归属时 RequestIdFormatter 渲染为 '-'
request_id_var: ContextVar[str] = ContextVar("request_id", default="")

# 与后端 logging.pattern.console 对齐：时间 级别 [requestId] logger - 消息
LOG_FORMAT = "%(asctime)s %(levelname)-5s [%(request_id)s] %(name)s - %(message)s"
LOG_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

_logger = logging.getLogger(__name__)


class RequestIdFormatter(logging.Formatter):
    """统一日志格式器：每条记录注入当前请求 ID。

    在 format() 阶段兜底赋值（而非 handler 级 Filter），保证所有经本格式器的
    记录——包括未显式传 request_id 的业务日志——都不会因缺属性触发 LoggingError；
    显式传 extra 的（访问日志在上下文复位后仍要携带 ID）保留调用方值。
    """

    def format(self, record: logging.LogRecord) -> str:
        if not hasattr(record, "request_id"):
            record.request_id = request_id_var.get() or "-"
        return super().format(record)


def setup_logging() -> None:
    """统一日志格式并接管 uvicorn 日志。

    幂等：dictConfig 每次全量重建配置，重复调用不会重复追加 handler。
    uvicorn 在 Config 构造期（app 导入前）先行应用默认日志配置，本函数在
    app 模块导入时调用、必然在其后执行，因此能覆盖根日志器并关闭重复的
    uvicorn.access 访问日志（由 RequestLogMiddleware 统一输出）。
    """
    logging.config.dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "default": {
                    "()": RequestIdFormatter,
                    "format": LOG_FORMAT,
                    "datefmt": LOG_DATE_FORMAT,
                }
            },
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "stream": "ext://sys.stderr",
                    "formatter": "default",
                }
            },
            "root": {"level": "INFO", "handlers": ["console"]},
            "loggers": {
                # uvicorn 自带 access 日志与自建中间件访问日志重复，且无 request_id，
                # 完全关闭；uvicorn 其他日志沿用统一格式输出
                "uvicorn.access": {"handlers": [], "propagate": False, "level": "WARNING"},
                "uvicorn.error": {"handlers": ["console"], "propagate": True, "level": "INFO"},
                "uvicorn": {"handlers": ["console"], "propagate": True, "level": "INFO"},
                # httpx/httpcore 默认把每个出站请求打一行 INFO（回调外发时噪音大），
                # 其失败/异常已在 TaskManager._callback 显式记录，这里压到 WARNING
                "httpx": {"propagate": True, "level": "WARNING"},
                "httpcore": {"propagate": True, "level": "WARNING"},
            },
        }
    )


class RequestLogMiddleware(BaseHTTPMiddleware):
    """请求日志中间件：跨服务 request_id 贯穿 + 一行访问日志。

    - 沿用调用方（后端）传入的 X-Request-Id，无则生成 uuid4 hex；
    - 响应回写 X-Request-Id，供调用方核对链路贯穿；
    - 请求上下文内所有业务日志自动携带该 request_id（见 RequestIdFormatter）；
    - 每个请求记录一行访问日志（方法/路径/状态/耗时/客户端）。
    """

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        request_id = request.headers.get(REQUEST_ID_HEADER) or uuid4().hex
        token = request_id_var.set(request_id)
        start = time.perf_counter()
        status = 500
        try:
            response = await call_next(request)
            response.headers[REQUEST_ID_HEADER] = request_id
            status = response.status_code
            return response
        finally:
            duration_ms = (time.perf_counter() - start) * 1000.0
            request_id_var.reset(token)
            client = request.client.host if request.client else "-"
            _logger.info(
                "access method=%s path=%s status=%s duration_ms=%.1f client=%s",
                request.method,
                request.url.path,
                status,
                duration_ms,
                client,
                extra={"request_id": request_id},
            )
