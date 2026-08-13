import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from redis.asyncio import Redis

from app.api.routes import router as api_router
from app.core.config import settings
from app.core.metrics import sample_queue_depth
from app.core.observability import RequestLogMiddleware, setup_logging
from app.llm import build_registry
from app.scheduler import Scheduler
from app.services import ServiceContext, build_services
from app.tasks.manager import TaskManager
from app.vectors import RedisVectorStore

# 统一日志格式与 request_id 贯穿：须在应用构造前完成。
# uvicorn 在 Config 构造期（app 导入前）先行应用默认日志配置，此处必然在其后
# 执行，可覆盖根日志器并关闭重复的 uvicorn.access 日志。
setup_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis = Redis.from_url(settings.redis_url, decode_responses=True)
    app.state.redis = redis

    providers = build_registry(settings)
    app.state.providers = providers

    vector_store = RedisVectorStore(redis, settings.vector_namespace_prefix)
    app.state.vector_store = vector_store

    context = ServiceContext(
        redis=redis,
        settings=settings,
        providers=providers,
        vectors=vector_store,
        scheduler=None,
    )
    services = build_services(context)
    task_manager = TaskManager(redis, settings, services=services)
    # 崩溃自愈：启动时回收租约过期的 RUNNING 遗留任务重新入队（见
    # TaskManager.recover_stale_tasks），避免进程被杀/滚动发布后任务永久卡死
    await task_manager.recover_stale_tasks()
    app.state.task_manager = task_manager

    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(task_manager)
    context.scheduler = scheduler
    app.state.scheduler = scheduler
    await scheduler.start()

    yield

    await task_manager.close()
    await scheduler.stop()
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

# 访问日志 + request_id 贯穿（最外层，先于 CORS 处理，覆盖全部请求）
app.add_middleware(RequestLogMiddleware)

app.include_router(api_router)


@app.get("/health", tags=["core"])
async def health(response: Response) -> dict[str, str]:
    # 探活检查依赖的 Redis；不可用时返回 503，供 k8s readiness/liveness 探针正确判定
    redis = getattr(app.state, "redis", None)
    if redis is not None:
        try:
            await redis.ping()
        except Exception:  # noqa: BLE001 - Redis 探活失败统一按不健康处理
            response.status_code = 503
            return {"status": "error", "service": settings.app_name, "detail": "redis unreachable"}
    return {"status": "ok", "service": settings.app_name}


@app.get("/metrics", tags=["core"])
async def metrics(request: Request) -> Response:
    # 供 Prometheus 抓取，不参与业务鉴权。根路径（与 /health 同级）而非 /api/v1 前缀：
    # 指标是基础设施端点，标准 scrape 路径就是根 /metrics，挂在 API 前缀下极易配错。
    # 抓取时先实时采样队列深度（LLEN/ZCARD 均 O(1)，见 app.core.metrics.sample_queue_depth），
    # 再在独立线程序列化——generate_latest 是同步 CPU 工作，直接在 async 端点内跑
    # 会阻塞事件循环，影响同进程所有请求。
    redis = getattr(request.app.state, "redis", None)
    if redis is not None:
        await sample_queue_depth(redis)
    return Response(content=await asyncio.to_thread(generate_latest), media_type=CONTENT_TYPE_LATEST)
