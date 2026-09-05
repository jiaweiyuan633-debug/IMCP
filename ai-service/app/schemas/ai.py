"""新 AI 能力入参/出参：对话、流式、Embedding、向量检索、定时管道。"""

from typing import Any

from pydantic import BaseModel, Field, field_validator

# 单条文本长度上限（与 ChatMessage.content / 任务路径 llm_chat 对齐），
# 防无界入参撑爆 LLM 上下文与出站带宽
MAX_TEXT_CHARS = 32_000


class ChatMessage(BaseModel):
    role: str = Field(pattern="^(system|user|assistant|tool)$")
    # 单条消息长度上限 32k 字符——无界入参可撑爆 LLM 上下文与出站带宽；超限由 Pydantic 422 拒绝
    content: str = Field(max_length=MAX_TEXT_CHARS)
    name: str | None = None


class ChatRequest(BaseModel):
    messages: list[ChatMessage] = Field(min_length=1, max_length=100)
    model: str | None = None
    temperature: float | None = Field(default=None, ge=0, le=2)
    max_tokens: int | None = Field(default=None, ge=1, le=32_000)
    provider: str | None = None
    # PII 出域开关：默认对发给外部 provider 的 user 文本与模型输出脱敏（可显式关停）。
    # 服务端强制开关 PII_MASK_REQUIRED=true 时，此字段为 false 也不绕过
    # （mock 等进程内提供方豁免，见 app.pii.outbound.should_mask_outbound）。
    mask_pii: bool = True


class ChatResponse(BaseModel):
    content: str
    model: str | None = None
    provider: str | None = None
    usage: dict | None = None
    # 本次输出命中的 PII 数量（未开启脱敏或未命中时为 0），供调用方感知脱敏生效情况
    pii_count: int = 0


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=128)
    model: str | None = None
    provider: str | None = None
    # 待向量化文本同样出域到外部 provider，开关语义与 ChatRequest.mask_pii 一致
    mask_pii: bool = True

    @field_validator("texts")
    @classmethod
    def _limit_text_sizes(cls, values: list[str]) -> list[str]:
        for text in values:
            if len(text) > MAX_TEXT_CHARS:
                raise ValueError(f"单条文本超 {MAX_TEXT_CHARS} 字符上限")
        return values


class EmbedResponse(BaseModel):
    vectors: list[list[float]]
    dim: int
    model: str | None = None
    provider: str | None = None


class VectorUpsertRequest(BaseModel):
    namespace: str = Field(min_length=1, max_length=128)
    doc_id: str = Field(min_length=1, max_length=255)
    text: str = Field(min_length=1, max_length=MAX_TEXT_CHARS)
    payload: dict[str, Any] = Field(default_factory=dict)
    provider: str | None = None
    mask_pii: bool = True


class VectorSearchRequest(BaseModel):
    namespace: str = Field(min_length=1, max_length=128)
    text: str | None = Field(default=None, max_length=MAX_TEXT_CHARS)
    # 向量维度上限：禁止提交超大原始向量撑爆检索线程与 Redis 内存
    vector: list[float] | None = Field(default=None, max_length=8_192)
    top_k: int = Field(default=5, ge=1, le=100)
    threshold: float = Field(default=0.0, ge=-1, le=1)
    provider: str | None = None
    mask_pii: bool = True


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
