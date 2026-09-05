import asyncio
import hmac
import json
from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings
from app.llm.retry import retry_llm_call
from app.pii import StreamMasker, detect, mask
from app.pii.outbound import (
    mask_outbound_messages,
    mask_outbound_texts,
    should_mask_outbound,
)
from app.schemas.ai import (
    ChatRequest,
    ChatResponse,
    EmbedRequest,
    EmbedResponse,
    ScheduleCreateRequest,
    ScheduleResponse,
    VectorHit,
    VectorSearchRequest,
    VectorSearchResponse,
    VectorUpsertRequest,
)
from app.schemas.task import DeadLetterEntry, TaskCreateRequest, TaskStatusResponse
from app.vectors.store import NamespaceTooLargeError

router = APIRouter(prefix="/api/v1", tags=["core"])
bearer_scheme = HTTPBearer(auto_error=False)


def require_api_token(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)] = None,
) -> None:
    """任务接口鉴权：后端提交/查询/重试任务时须出示共享密钥（Authorization: Bearer）。

    AI 服务位于集群内部，仍做纵深防御——密钥与后端 AiServiceConfig.apiKey 一致。
    流式/向量/调度等新增接口同样受保护。
    """
    if credentials is None or not hmac.compare_digest(credentials.credentials, settings.auth_token):
        raise HTTPException(status_code=401, detail="unauthorized")


def _resolve_provider(request: Request, name: str | None) -> Any:
    """解析调用方指定的 provider；未知名称属客户端错误返回 400，而非服务端 500。

    修复前 ProviderRegistry.get 对未知名称抛 KeyError，FastAPI 未捕获 → 500。
    /chat、/chat/stream、/embeddings、/vectors/upsert、/vectors/search 全部走此
    函数统一收敛，避免每个端点各自 try/except。
    """
    providers = request.app.state.providers
    if name:
        try:
            return providers.get(name)
        except KeyError:
            raise HTTPException(status_code=400, detail=f"unknown provider: {name}") from None
    return providers.default()


@router.get("/ping")
async def ping() -> dict[str, str]:
    return {"message": "pong", "service": "ai-service"}


# ---------- 任务队列 ----------

@router.post(
    "/tasks",
    status_code=202,
    response_model=TaskStatusResponse,
    dependencies=[Depends(require_api_token)],
)
async def create_task(request: Request, payload: TaskCreateRequest) -> TaskStatusResponse:
    request_id = request.headers.get("X-Request-Id")
    return await request.app.state.task_manager.create_task(payload, request_id)


@router.get(
    "/tasks/dead",
    response_model=list[DeadLetterEntry],
    dependencies=[Depends(require_api_token)],
)
async def list_dead_tasks(
    request: Request,
    limit: int = Query(default=100, ge=1, le=1000),
) -> list[DeadLetterEntry]:
    """列出死信队列（新失败在前），limit 控制返回条数（默认 100，上限 1000）。

    运维面端点：死信为只写 Redis list，无此接口前无法查询失败历史，只能等新
    失败把旧记录挤出裁剪窗口。修复前旧记录缺 failed_at/biz_type 等富化字段，
    由 DeadLetterEntry 默认值兼容透出。
    """
    return await request.app.state.task_manager.list_dead_letters(limit)


@router.delete(
    "/tasks/dead",
    dependencies=[Depends(require_api_token)],
)
async def purge_dead_tasks(request: Request) -> dict[str, int]:
    """清空死信队列并返回清理条数（运维清障后收尾）。"""
    purged = await request.app.state.task_manager.purge_dead_letters()
    return {"purged": purged}


@router.get(
    "/tasks/{task_id}",
    response_model=TaskStatusResponse,
    dependencies=[Depends(require_api_token)],
)
async def get_task(request: Request, task_id: str) -> TaskStatusResponse:
    task = await request.app.state.task_manager.get_task(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="task not found")
    return task


@router.post(
    "/tasks/{task_id}/retry",
    response_model=TaskStatusResponse,
    dependencies=[Depends(require_api_token)],
)
async def retry_task(request: Request, task_id: str) -> TaskStatusResponse:
    return await request.app.state.task_manager.retry(task_id)


# ---------- 对话（真实 LLM，多供应商） ----------

@router.post(
    "/chat",
    response_model=ChatResponse,
    dependencies=[Depends(require_api_token)],
)
async def chat(request: Request, payload: ChatRequest) -> ChatResponse:
    # 非任务路径（同步调用）无 TaskManager 重试，LLM 可重试异常（网络/5xx）须退避重试
    provider = _resolve_provider(request, payload.provider)
    messages = [m.model_dump() for m in payload.messages]
    # 出站 PII 脱敏——发给外部 LLM 的 user 消息中的手机号/身份证等先脱敏
    # （PIPL/等保对敏感数据出域的控制）。mask_pii 为调用方开关；服务端强制
    # 开关 PII_MASK_REQUIRED 开启时 mask_pii=false 也不绕过。detect/mask 是
    # CPU 密集的同步函数，放线程池执行避免阻塞事件循环。
    mask_enabled = should_mask_outbound(settings, provider, payload.mask_pii)
    if mask_enabled:
        messages = await asyncio.to_thread(
            mask_outbound_messages, messages, settings.pii_mask_char
        )
    content = await retry_llm_call(
        provider.chat,
        messages,
        model=payload.model,
        temperature=payload.temperature,
        max_tokens=payload.max_tokens,
        max_attempts=settings.llm_retry_max_attempts,
        base_delay=settings.llm_retry_base_seconds,
    )
    # 输出侧同样处理：模型回复若复述了敏感信息，脱敏后再返回；命中数随响应透出
    pii_count = 0
    if mask_enabled:
        detected = await asyncio.to_thread(detect, content)
        if detected:
            content = await asyncio.to_thread(mask, content, settings.pii_mask_char)
            pii_count = len(detected)
    return ChatResponse(
        content=content,
        model=payload.model or getattr(provider, "default_model", None),
        provider=getattr(provider, "name", None),
        pii_count=pii_count,
    )


@router.post(
    "/chat/stream",
    dependencies=[Depends(require_api_token)],
)
async def chat_stream(request: Request, payload: ChatRequest) -> StreamingResponse:
    """SSE 流式对话：逐段产出 ``data: {"delta": ...}``，结束以 ``data: [DONE]`` 标记。

    内部服务消费方（后端代理）通过 Authorization 头鉴权；
    浏览器 EventSource 无法带自定义头，须经后端 /api 代理转发。
    注：流式已开始产出即无法安全重试（用户已看到部分内容），上游故障由消费方决定是否重连。
    PII 强制开启时，入站 user 消息先脱敏再发给 provider，输出经 StreamMasker
    逐段脱敏（跨分片 PII 不泄漏），分片边界可能与上游不同，消费方应按 delta 拼装全文。
    """
    provider = _resolve_provider(request, payload.provider)
    messages = [m.model_dump() for m in payload.messages]
    # 入站出域脱敏与 /chat 一致（此前流式路径缺省未脱敏，PII 可原样出域）
    mask_enabled = should_mask_outbound(settings, provider, payload.mask_pii)
    if mask_enabled:
        messages = await asyncio.to_thread(
            mask_outbound_messages, messages, settings.pii_mask_char
        )
    masker = StreamMasker(settings.pii_mask_char) if mask_enabled else None

    async def event_stream() -> Any:
        async for delta in provider.stream(
            messages,
            model=payload.model,
            temperature=payload.temperature,
            max_tokens=payload.max_tokens,
        ):
            if masker is None:
                yield f"data: {json.dumps({'delta': delta}, ensure_ascii=False)}\n\n"
            else:
                for chunk in masker.emit(delta):
                    yield f"data: {json.dumps({'delta': chunk}, ensure_ascii=False)}\n\n"
        if masker is not None:
            tail = masker.flush()
            if tail:
                yield f"data: {json.dumps({'delta': tail}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


# ---------- Embedding 与向量检索 ----------

@router.post(
    "/embeddings",
    response_model=EmbedResponse,
    dependencies=[Depends(require_api_token)],
)
async def embed(request: Request, payload: EmbedRequest) -> EmbedResponse:
    provider = _resolve_provider(request, payload.provider)
    # 待向量化文本同样出域到外部 provider：脱敏开关判定与 chat 共用同一出口
    texts = payload.texts
    if should_mask_outbound(settings, provider, payload.mask_pii):
        texts = await asyncio.to_thread(
            mask_outbound_texts, texts, settings.pii_mask_char
        )
    vectors = await provider.embed(texts, model=payload.model)
    return EmbedResponse(
        vectors=vectors,
        dim=len(vectors[0]) if vectors else 0,
        model=payload.model or getattr(provider, "embedding_model", None),
        provider=getattr(provider, "name", None),
    )


@router.post(
    "/vectors/upsert",
    dependencies=[Depends(require_api_token)],
)
async def vector_upsert(request: Request, payload: VectorUpsertRequest) -> dict[str, Any]:
    provider = _resolve_provider(request, payload.provider)
    # text → embed 前脱敏（同一出口），保证向量库不落入原文
    text = payload.text
    if should_mask_outbound(settings, provider, payload.mask_pii):
        text = await asyncio.to_thread(mask, text, settings.pii_mask_char)
    vector = (await provider.embed([text]))[0]
    await request.app.state.vector_store.upsert(payload.namespace, payload.doc_id, vector, payload.payload)
    return {"namespace": payload.namespace, "doc_id": payload.doc_id, "dim": len(vector)}


@router.post(
    "/vectors/search",
    response_model=VectorSearchResponse,
    dependencies=[Depends(require_api_token)],
)
async def vector_search(request: Request, payload: VectorSearchRequest) -> VectorSearchResponse:
    if payload.vector is None:
        if not payload.text:
            raise HTTPException(status_code=400, detail="text 或 vector 至少提供一个")
        provider = _resolve_provider(request, payload.provider)
        # 查询文本经外部 provider 向量化前同样脱敏
        text = payload.text
        if should_mask_outbound(settings, provider, payload.mask_pii):
            text = await asyncio.to_thread(mask, text, settings.pii_mask_char)
        query_vector = (await provider.embed([text]))[0]
    else:
        query_vector = payload.vector
    try:
        hits = await request.app.state.vector_store.search(
            payload.namespace, query_vector, top_k=payload.top_k, threshold=payload.threshold
        )
    except NamespaceTooLargeError as exc:
        # 命名空间超精确检索上限：属容量类客户端错误，返回 422 而非 500
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return VectorSearchResponse(namespace=payload.namespace, hits=[VectorHit(**hit) for hit in hits])


# ---------- 定时管道 ----------

@router.post(
    "/schedules",
    response_model=ScheduleResponse,
    dependencies=[Depends(require_api_token)],
)
async def create_schedule(request: Request, payload: ScheduleCreateRequest) -> ScheduleResponse:
    try:
        return await request.app.state.scheduler.register(
            payload.name, payload.schedule, payload.biz_type, payload.params, payload.enabled
        )
    except ValueError as exception:
        # 触发表达式非法属客户端错误（422），而非服务端故障（500）
        raise HTTPException(status_code=422, detail=str(exception)) from exception


@router.get(
    "/schedules",
    dependencies=[Depends(require_api_token)],
)
async def list_schedules(request: Request) -> list[ScheduleResponse]:
    return await request.app.state.scheduler.list_schedules()


@router.delete(
    "/schedules/{schedule_id}",
    dependencies=[Depends(require_api_token)],
)
async def delete_schedule(request: Request, schedule_id: str) -> dict[str, str]:
    await request.app.state.scheduler.unregister(schedule_id)
    return {"deleted": schedule_id}


