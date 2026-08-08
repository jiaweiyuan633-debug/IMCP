from typing import Any

from pydantic import BaseModel, Field


class TaskCreateRequest(BaseModel):
    task_no: str = Field(min_length=1, max_length=64)
    biz_type: str = Field(min_length=1)
    params: dict[str, Any] = Field(default_factory=dict)
    callback_url: str | None = None


class TaskStatusResponse(BaseModel):
    task_no: str
    biz_type: str
    status: str
    params: dict[str, Any] = Field(default_factory=dict)
    result: Any = None
    error: str | None = None
    retry_count: int = 0
    max_retry: int = 3
    created_at: str | None = None
    updated_at: str | None = None

