from fastapi import APIRouter

router = APIRouter(prefix="/api/v1", tags=["core"])


@router.get("/ping")
async def ping() -> dict[str, str]:
    return {"message": "pong", "service": "ai-service"}

