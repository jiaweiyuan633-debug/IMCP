from typing import Any

from pydantic import BaseModel, Field


class TaskCreateRequest(BaseModel):
    task_no: str = Field(min_length=1, max_length=64)
    biz_type: str = Field(min_length=1)
    params: dict[str, Any] = Field(default_factory=dict)
    callback_url: str | None = None
    # 任务队列深化：优先级（0-9，越大越先执行，默认 5）与超时（秒，默认取服务配置）
    priority: int = Field(default=5, ge=0, le=9)
    timeout: float | None = Field(default=None, gt=0)


class TaskStatusResponse(BaseModel):
    task_no: str
    biz_type: str
    status: str
    params: dict[str, Any] = Field(default_factory=dict)
    result: Any = None
    error: str | None = None
    retry_count: int = 0
    max_retry: int = 3
    priority: int = 5
    timeout: float | None = None
    created_at: str | None = None
    updated_at: str | None = None
    # R4-1.20：失败原因分类（timeout / non_retryable / retries_exhausted），未失败为 None
    reason: str | None = None

