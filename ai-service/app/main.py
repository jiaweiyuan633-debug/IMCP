from contextlib import asynccontextmanager

from fastapi import FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from redis.asyncio import Redis

from app.api.routes import router as api_router
from app.core.config import settings
from app.tasks.manager import TaskManager


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis = Redis.from_url(settings.redis_url, decode_responses=True)
    app.state.task_manager = TaskManager(redis, settings)
    app.state.redis = redis
    yield
    await redis.aclose()

app = FastAPI(title=settings.app_name, version="0.1.0", lifespan=lifespan)

# AI 服务为集群内部服务，无浏览器消费者：默认不开放跨域。
# 如需浏览器直连，通过 CORS_ORIGINS 显式配置允许来源（且不携带凭证）。
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=bool(settings.cors_origins),
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router)


@app.get("/health", tags=["core"])
async def health(response: Response) -> dict[str, str]:
    # 探活检查依赖的 Redis；不可用时返回 503，供 k8s readiness/liveness 探针正确判定
    redis = getattr(app.state, "redis", None)
    if redis is not None:
        try:
            await redis.ping()
        except Exception:
            response.status_code = 503
            return {"status": "error", "service": settings.app_name, "detail": "redis unreachable"}
    return {"status": "ok", "service": settings.app_name}
