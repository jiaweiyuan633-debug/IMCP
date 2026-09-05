"""LLM 调用重试（非任务路径专用）。

任务路径（TaskManager）已有任务级重试 + 死信队列，本模块只服务同步 /chat 与
Agent 引擎等非任务调用点：对可重试异常（``LLMError``：网络抖动/超时/上游 5xx）
做指数退避重试，配置/认证异常（``LLMConfigError``）无重试意义，立即上抛。

重试安全性：大模型补全是幂等的只读操作，无副作用，重试不会造成重复副作用。
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import TypeVar

from app.llm.base import LLMConfigError, LLMError

logger = logging.getLogger(__name__)

T = TypeVar("T")


async def retry_llm_call(
    operation: Callable[..., Awaitable[T]],
    *args,
    max_attempts: int = 3,
    base_delay: float = 0.5,
    max_delay: float = 8.0,
    **kwargs,
) -> T:
    """以指数退避重试执行一次 LLM 调用。

    - ``LLMConfigError``：立即上抛（认证/模型不存在等，重试无意义）；
    - ``LLMError``：最多 ``max_attempts`` 次尝试，退避 ``base_delay * 2^(n-1)``、封顶 ``max_delay``；
    - 耗尽后上抛最后一次 ``LLMError``。
    """
    last_error: LLMError | None = None
    for attempt in range(max_attempts):
        try:
            return await operation(*args, **kwargs)
        except LLMConfigError:
            raise
        except LLMError as exception:
            last_error = exception
            if attempt >= max_attempts - 1:
                break
            delay = min(base_delay * (2**attempt), max_delay)
            logger.warning(
                "LLM 调用失败，第 %d/%d 次重试（%.1fs 后）：%s",
                attempt + 2,
                max_attempts,
                delay,
                exception,
            )
            await asyncio.sleep(delay)
    assert last_error is not None  # max_attempts >= 1 时必然至少进入一次 except
    raise last_error
