"""新 AI 能力入参/出参：对话、流式、Embedding、向量检索、定时管道。"""

from typing import Any

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    role: str = Field(pattern="^(system|user|assistant|tool)$")
    # 批次3（R4-1.49）：单条消息长度上限 32k 字符——无界入参可撑爆 LLM 上下文
    # 与出站带宽，且放大成本；超限由 Pydantic 422 直接拒绝
    content: str = Field(max_length=32_000)
    name: str | None = None


class ChatRequest(BaseModel):
    messages: list[ChatMessage] = Field(min_length=1, max_length=100)
    model: str | None = None
    temperature: float | None = Field(default=None, ge=0, le=2)
    max_tokens: int | None = Field(default=None, ge=1, le=32_000)
    provider: str | None = None
    # R4-1.34：PII 强制——默认对模型输出脱敏（可显式关停）。模型可能在回复中复述
    # 输入里的手机号/身份证号等敏感信息，出站前统一脱敏，防止敏感数据外泄。
    # 批次3：mask_pii=True 时同样对**出站 user 消息**脱敏（输入 PII 不落外部 LLM），
    # 满足 PIPL/等保对敏感数据出域的控制要求
    mask_pii: bool = True


class ChatResponse(BaseModel):
    content: str
    model: str | None = None
    provider: str | None = None
    usage: dict | None = None
    # R4-1.34：本次输出命中的 PII 数量（mask_pii=False 或未命中时为 0），
    # 供调用方感知脱敏生效情况
    pii_count: int = 0


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=128)
    model: str | None = None
    provider: str | None = None


class EmbedResponse(BaseModel):
    vectors: list[list[float]]
    dim: int
    model: str | None = None
    provider: str | None = None


class VectorUpsertRequest(BaseModel):
    namespace: str = Field(min_length=1, max_length=128)
    doc_id: str = Field(min_length=1, max_length=255)
    text: str = Field(min_length=1)
    payload: dict[str, Any] = Field(default_factory=dict)
    provider: str | None = None


class VectorSearchRequest(BaseModel):
    namespace: str = Field(min_length=1, max_length=128)
    text: str | None = None
    vector: list[float] | None = None
    top_k: int = Field(default=5, ge=1, le=100)
    threshold: float = Field(default=0.0, ge=-1, le=1)
    provider: str | None = None


class VectorHit(BaseModel):
    doc_id: str
    score: float
    payload: dict[str, Any] = Field(default_factory=dict)


class VectorSearchResponse(BaseModel):
    namespace: str
    hits: list[VectorHit]


class ScheduleCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    # 触发表达式："interval:60"（每 60 秒）或 "cron:* * * * *"（5 段：分 时 日 月 周）
    schedule: str = Field(min_length=1, max_length=64)
    biz_type: str = Field(min_length=1)
    params: dict[str, Any] = Field(default_factory=dict)
    enabled: bool = True


class ScheduleResponse(BaseModel):
    id: str
    name: str
    schedule: str
    biz_type: str
    params: dict[str, Any] = Field(default_factory=dict)
    enabled: bool
    next_run_at: float | None = None
    run_count: int = 0
