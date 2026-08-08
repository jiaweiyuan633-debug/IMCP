from fastapi import APIRouter, HTTPException, Request

from app.schemas.task import TaskCreateRequest, TaskStatusResponse

router = APIRouter(prefix="/api/v1", tags=["core"])


@router.get("/ping")
async def ping() -> dict[str, str]:
    return {"message": "pong", "service": "ai-service"}


@router.post("/tasks", status_code=202, response_model=TaskStatusResponse)
async def create_task(request: Request, payload: TaskCreateRequest) -> TaskStatusResponse:
    return await request.app.state.task_manager.create_task(payload)


@router.get("/tasks/{task_id}", response_model=TaskStatusResponse)
async def get_task(request: Request, task_id: str) -> TaskStatusResponse:
    task = await request.app.state.task_manager.get_task(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="task not found")
    return task


@router.post("/tasks/{task_id}/retry", response_model=TaskStatusResponse)
async def retry_task(request: Request, task_id: str) -> TaskStatusResponse:
    return await request.app.state.task_manager.retry(task_id)
