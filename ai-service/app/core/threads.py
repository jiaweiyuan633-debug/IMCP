"""CPU 密集工作的有界线程池执行器。

``asyncio.wait_for`` 只能取消 await 的协程，无法取消 ``asyncio.to_thread`` 里
已经在线程中执行的不可中断工作（OCR、PDF 解析、KMeans、TF-IDF 训练等）：
外层超时后线程仍在占用 CPU 与内存。本模块集中管理一个进程级、有界、随应用
关闭释放的线程池，把不可中断工作收敛到受控的并发上限内：

- 并发上限：``max_workers`` 默认取 settings.cpu_thread_pool_size（应用启动时
  configure 注入），杜绝无界线程膨胀与同进程请求被 CPU 工作挤占；
- 超时语义：调用方仍可用 ``asyncio.wait_for`` 控制 await 返回；配合各服务
  输入侧的规模预检（超大输入在提交前拒绝），把线程占用限制在可接受范围；
- 生命周期：应用关闭时 ``shutdown`` 释放线程池（不等待在途任务，避免拖慢停机）。
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from typing import Any

logger = logging.getLogger(__name__)

_executor: ThreadPoolExecutor | None = None
_max_workers: int = 4


def configure(max_workers: int) -> None:
    """设置线程池并发上限；须在首次提交 CPU 工作前调用。"""
    global _max_workers
    _max_workers = max(1, int(max_workers))


def _ensure_executor() -> ThreadPoolExecutor:
    global _executor
    if _executor is None:
        _executor = ThreadPoolExecutor(max_workers=_max_workers, thread_name_prefix="ai-cpu")
        logger.info("CPU 工作线程池已创建：max_workers=%s", _max_workers)
    return _executor


async def run_cpu(func: Callable[..., Any], *args: Any, **kwargs: Any) -> Any:
    """把 CPU 密集的同步调用放入有界线程池执行并返回结果。

    不可中断工作只能控制并发上限与输入规模；调用方如需总时长兜底，
    可在外层套 ``asyncio.wait_for``（超时只能放弃等待、无法终止线程）。
    """
    executor = _ensure_executor()
    if kwargs:
        return await asyncio.get_running_loop().run_in_executor(
            executor, _call_with_kwargs, func, args, kwargs
        )
    return await asyncio.get_running_loop().run_in_executor(executor, func, *args)


def _call_with_kwargs(func: Callable[..., Any], args: tuple, kwargs: dict) -> Any:
    return func(*args, **kwargs)


def shutdown(wait: bool = False) -> None:
    """应用关闭时释放线程池：默认不等待在途任务、取消未启动的 future。"""
    global _executor
    executor, _executor = _executor, None
    if executor is not None:
        executor.shutdown(wait=wait, cancel_futures=True)
