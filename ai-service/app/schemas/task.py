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
    # 失败原因分类（timeout / non_retryable / retries_exhausted），未失败为 None
    reason: str | None = None


class DeadLetterEntry(BaseModel):
    """死信队列单条记录（GET /api/v1/tasks/dead 返回）。

    字段与 TaskManager._record_dead_letter 写入结构一致：富化记录含
    biz_type / retry_count / failed_at，供运维按时间排障；修复前的旧记录缺
    这些字段，置默认值（None / 0）向后兼容。
    """
    task_no: str
    biz_type: str | None = None
    error: str | None = None
    reason: str | None = None
    retry_count: int = 0
    failed_at: str | None = None

