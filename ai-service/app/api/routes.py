import hmac
import json
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response, StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app.core.config import settings
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
from app.schemas.task import TaskCreateRequest, TaskStatusResponse

router = APIRouter(prefix="/api/v1", tags=["core"])
bearer_scheme = HTTPBearer(auto_error=False)


def require_api_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> None:
    """任务接口鉴权：后端提交/查询/重试任务时须出示共享密钥（Authorization: Bearer）。

    AI 服务位于集群内部，仍做纵深防御——密钥与后端 AiServiceConfig.apiKey 一致。
    流式/向量/调度等新增接口同样受保护。
    """
    if credentials is None or not hmac.compare_digest(credentials.credentials, settings.auth_token):
        raise HTTPException(status_code=401, detail="unauthorized")


def _resolve_provider(request: Request, name: str | None) -> Any:
    providers = request.app.state.providers
    return providers.get(name) if name else providers.default()


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
    provider = _resolve_provider(request, payload.provider)
    content = await provider.chat(
        [m.model_dump() for m in payload.messages],
        model=payload.model,
        temperature=payload.temperature,
        max_tokens=payload.max_tokens,
    )
    return ChatResponse(
        content=content,
        model=payload.model or getattr(provider, "default_model", None),
        provider=getattr(provider, "name", None),
    )


@router.post(
    "/chat/stream",
    dependencies=[Depends(require_api_token)],
)
async def chat_stream(request: Request, payload: ChatRequest) -> StreamingResponse:
    """SSE 流式对话：逐段产出 ``data: {"delta": ...}``，结束以 ``data: [DONE]`` 标记。

    内部服务消费方（后端代理）通过 Authorization 头鉴权；
    浏览器 EventSource 无法带自定义头，须经后端 /api 代理转发。
    """
    provider = _resolve_provider(request, payload.provider)

    async def event_stream() -> Any:
        async for delta in provider.stream(
            [m.model_dump() for m in payload.messages],
            model=payload.model,
            temperature=payload.temperature,
            max_tokens=payload.max_tokens,
        ):
            yield f"data: {json.dumps({'delta': delta}, ensure_ascii=False)}\n\n"
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
    vectors = await provider.embed(payload.texts, model=payload.model)
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
    vector = (await provider.embed([payload.text]))[0]
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
        query_vector = (await provider.embed([payload.text]))[0]
    else:
        query_vector = payload.vector
    hits = await request.app.state.vector_store.search(
        payload.namespace, query_vector, top_k=payload.top_k, threshold=payload.threshold
    )
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


@router.get("/metrics")
async def metrics() -> Response:
    # 供 Prometheus 抓取，不参与业务鉴权
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
