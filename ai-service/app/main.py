import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from redis.asyncio import Redis

from app.api.routes import router as api_router
from app.core.config import settings
from app.core.errors import register_exception_handlers
from app.core.metrics import sample_queue_depth
from app.core.observability import RequestLogMiddleware, setup_logging
from app.core.threads import configure as configure_cpu_pool
from app.core.threads import shutdown as shutdown_cpu_pool
from app.llm import build_registry
from app.scheduler import Scheduler
from app.services import ServiceContext, build_services
from app.tasks.manager import TaskManager
from app.vectors import RedisVectorStore

# 统一日志格式与 request_id 贯穿：须在应用构造前完成。
# uvicorn 在 Config 构造期（app 导入前）先行应用默认日志配置，此处必然在其后
# 执行，可覆盖根日志器并关闭重复的 uvicorn.access 日志。
setup_logging()


class RequestBodyLimitMiddleware:
    """全局请求体大小上限中间件：超过 max_bytes 的请求体返回 413。

    AI 服务接口（含任务建单）的 params 无边界，超大 payload 会撑爆内存与下游
    Redis/LLM；Content-Length 已超限时直接拒绝（零读取开销），其余在读取
    receive 流时计数、超限即中止。
    """

    def __init__(self, app, max_bytes: int) -> None:
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope, receive, send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        for name, value in scope.get("headers") or []:
            if name == b"content-length":
                try:
                    if int(value) > self.max_bytes:
                        await self._reject(scope, receive, send)
                        return
                except ValueError:
                    pass
                break
        total = 0

        async def limited_receive():
            nonlocal total
            message = await receive()
            if message["type"] == "http.request":
                total += len(message.get("body", b""))
                if total > self.max_bytes:
                    raise BodyTooLarge()
            return message

        try:
            await self.app(scope, limited_receive, send)
        except BodyTooLarge:
            await self._reject(scope, receive, send)

    async def _reject(self, scope, receive, send) -> None:
        response = JSONResponse(
            {"detail": f"request body exceeds {self.max_bytes} bytes limit", "error_code": "body_too_large"},
            status_code=413,
        )
        await response(scope, receive, send)


class BodyTooLarge(Exception):
    """请求体超过上限（内部信号，由中间件转换为 413 响应）。"""


def _build_redis() -> Redis:
    """构造 Redis 客户端（见 app.core.config 的零超时说明）。

    连接/命令 socket 超时 + 空闲连接 health check + 超时重试，防止断连/卡死让
    worker、调度与探活永久阻塞。测试用 FakeRedis.from_url 会把未知连接参数透传
    给真实连接池导致挂起，故额外连接参数仅对真实 redis.asyncio.Redis 生效。
    """
    from redis.asyncio import Redis as _RealRedis

    kwargs: dict = {"decode_responses": True}
    if Redis is _RealRedis:
        kwargs.update(
            socket_connect_timeout=settings.redis_socket_connect_timeout,
            socket_timeout=settings.redis_socket_timeout,
            health_check_interval=settings.redis_health_check_interval,
            retry_on_timeout=True,
        )
    return Redis.from_url(settings.redis_url, **kwargs)


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis = _build_redis()
    app.state.redis = redis

    # CPU 密集不可中断工作的有界线程池（OCR/PDF/KMeans 等，见 app.core.threads）
    configure_cpu_pool(settings.cpu_thread_pool_size)

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
    # 崩溃自愈：启动时回收租约过期的 RUNNING 遗留任务与「QUEUED 但不在任何队列」
    # 的孤儿重新入队（见 TaskManager.recover_stale_tasks），避免进程被杀/滚动
    # 发布后任务永久卡死。
    await task_manager.recover_stale_tasks()
    # 启动即拉起工作线程——否则进程重启后队列残留任务（QUEUED/delayed）若无
    # 新提交触发 _ensure_workers，遗留任务将永远无人消费（worker 仅在 create_task/retry
    # 时被动启动）
    task_manager.ensure_workers()
    app.state.task_manager = task_manager

    scheduler = Scheduler(redis, settings)
    scheduler.attach_task_manager(task_manager)
    context.scheduler = scheduler
    app.state.scheduler = scheduler
    await scheduler.start()

    yield

    await task_manager.close()
    await scheduler.stop()
    # 释放 LLM 提供方连接池（httpx 持久 client），避免关闭阶段残留连接
    await providers.aclose_all()
    await redis.aclose()
    # 释放 CPU 工作线程池（不等待在途任务，避免拖慢停机）
    shutdown_cpu_pool()


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
# 全局请求体大小上限（413），包在最内层、仅包住路由处理
app.add_middleware(RequestBodyLimitMiddleware, max_bytes=settings.max_request_body_bytes)

# 统一异常处理：LLM 上游故障 → 502/504、稳定错误结构带 request_id
register_exception_handlers(app)

app.include_router(api_router)


async def _ping_redis(app: FastAPI, timeout: float = 2.0) -> bool:
    """Redis 探活：asyncio.wait_for 兜底，避免 ping 永久挂起拖住探针。"""
    redis = getattr(app.state, "redis", None)
    if redis is None:
        return True  # 初始化尚未就绪：不算不健康
    try:
        await asyncio.wait_for(redis.ping(), timeout=timeout)
        return True
    except Exception:  # noqa: BLE001 —— Redis 探活失败统一按不健康处理
        return False


@app.get("/health", tags=["core"])
async def health(response: Response) -> dict[str, str]:
    # 探活检查依赖的 Redis；不可用时返回 503，供 k8s readiness 探针正确判定
    if not await _ping_redis(app):
        response.status_code = 503
        return {"status": "error", "service": settings.app_name, "detail": "redis unreachable"}
    return {"status": "ok", "service": settings.app_name}


@app.get("/livez", tags=["core"])
async def livez() -> dict[str, str]:
    """纯进程存活探针——恒 200，供 k8s liveness 使用。

    原实现 liveness 打 /health（依赖 Redis），Redis 抖动时所有副本 CrashLoopBackOff；
    /livez 只反映进程本身，避免探针重启循环。
    """
    return {"status": "ok", "service": settings.app_name}


@app.get("/readyz", tags=["core"])
async def readyz(response: Response) -> dict[str, str]:
    """就绪探针——依赖 Redis，供 k8s readiness 使用。"""
    if not await _ping_redis(app):
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
