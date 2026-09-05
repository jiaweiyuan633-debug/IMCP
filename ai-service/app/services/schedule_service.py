"""定时管道注册任务服务：biz_type=schedule_register。

入参：{"name", "schedule"("interval:N" 或 "cron:* * * * *"), "biz_type", "params"?, "enabled"?}
出参：注册后的调度信息（含 id 与 next_run_at）
"""

from __future__ import annotations

from typing import Any

from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.tasks.errors import NonRetryableError


class ScheduleRegisterService(BaseTaskService):
    def __init__(self, context: ServiceContext) -> None:
        self.context = context

    async def run(self, params: dict[str, Any]) -> dict[str, Any]:
        scheduler = self.context.scheduler
        if scheduler is None:
            raise NonRetryableError("调度器未就绪")
        name = params.get("name")
        schedule = params.get("schedule")
        biz_type = params.get("biz_type")
        if not name or not schedule or not biz_type:
            raise ValueError("name/schedule/biz_type 必填")
        return await scheduler.register(
            name=name,
            schedule=schedule,
            biz_type=biz_type,
            params=params.get("params", {}),
            enabled=bool(params.get("enabled", True)),
        )
