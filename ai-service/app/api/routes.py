import hmac

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app.core.config import settings
from app.schemas.task import TaskCreateRequest, TaskStatusResponse

router = APIRouter(prefix="/api/v1", tags=["core"])
bearer_scheme = HTTPBearer(auto_error=False)


def require_api_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> None:
    """任务接口鉴权：后端提交/查询/重试任务时须出示共享密钥（Authorization: Bearer）。

    AI 服务位于集群内部，仍做纵深防御——密钥与后端 AiServiceConfig.apiKey 一致。
    """
    if credentials is None or not hmac.compare_digest(credentials.credentials, settings.auth_token):
        raise HTTPException(status_code=401, detail="unauthorized")


@router.get("/ping")
async def ping() -> dict[str, str]:
    return {"message": "pong", "service": "ai-service"}


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


@router.get("/metrics")
async def metrics() -> Response:
    # 供 Prometheus 抓取，不参与业务鉴权
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
