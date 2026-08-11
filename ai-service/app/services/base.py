"""任务服务基类：所有 biz_type 执行器实现 ``async run(params) -> dict``。"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any


class BaseTaskService(ABC):
    """AI 任务执行器统一接口。"""

    @abstractmethod
    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        """执行任务并返回结果字典；失败时抛出异常（由任务管理器按分类处理）。"""
