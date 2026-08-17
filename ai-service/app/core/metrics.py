"""应用级 Prometheus 指标（R4-1.8）。

默认注册表已含进程级指标（process_* / python_* 等），/metrics 由 main.py 提供
（根路径，批次5·R4-1.51 修正过时注释——此前误述由 routes.py 提供）。
此处补充 ai-service 业务指标，让运维在 Prometheus/Grafana 里直接观察任务吞吐与
队列水位，而不必逐个查 Redis：

- 任务生命周期计数器：创建 / 成功 / 失败（按原因）/ 进入重试；
- 队列深度 gauge：ready（待执行）/ delayed（退避中）/ dead（死信）三条队列的水位。

约定：
- 失败原因标签 reason：timeout（执行超时）/ non_retryable（不可重试）/ retries_exhausted（重试耗尽）；
- 队列深度需 Redis，抓取时实时采样（LLEN/ZCARD 均 O(1)，见 sample_queue_depth）。
  Redis 不可用时三个深度置 -1 表示「未知」，/metrics 本身仍返回 200，Prometheus
  抓取不中断（可在告警规则里用 queue_depth == -1 判定存储侧异常）。
"""

from typing import Any

from prometheus_client import Counter, Gauge

from app.core.config import settings

# 任务生命周期（计数，进程内累加）
ai_task_created_total = Counter(
    "ai_task_created_total", "创建的任务总数（409 拒绝等未合法建单的不计）"
)
ai_task_succeeded_total = Counter("ai_task_succeeded_total", "执行成功的任务总数")
ai_task_failed_total = Counter(
    "ai_task_failed_total", "失败的任务总数（按失败原因分标签）", labelnames=("reason",)
)
ai_task_retried_total = Counter("ai_task_retried_total", "进入重试（延迟队列）的任务次数")

# 队列与工作线程（gauge，绝对水位）
ai_queue_depth = Gauge("ai_queue_depth", "队列当前深度（ready/delayed/dead）", labelnames=("queue",))
ai_worker_count = Gauge("ai_worker_count", "当前运行的任务消费工作协程数")


async def sample_queue_depth(redis: Any) -> None:
    """抓取时采样三条队列深度并写入 gauge（见模块 docstring 约定）。

    Redis 不可用时写 -1 而不抛错：/metrics 是监控面，不能因存储侧故障反噬成
    scrape 失败。redis 可为 None（应用尚未完成 lifespan 初始化）——同样按未知处理。
    """
    if redis is None:
        _set_depths(-1, -1, -1)
        return
    try:
        ready = await redis.zcard(settings.queue_ready_key)
        delayed = await redis.zcard(settings.queue_delayed_key)
        dead = await redis.llen(settings.queue_dead_key)
    except Exception:  # noqa: BLE001 —— 采样失败不拖垮 /metrics，深度置未知
        _set_depths(-1, -1, -1)
        return
    _set_depths(ready, delayed, dead)


def _set_depths(ready: int, delayed: int, dead: int) -> None:
    ai_queue_depth.labels(queue="ready").set(ready)
    ai_queue_depth.labels(queue="delayed").set(delayed)
    ai_queue_depth.labels(queue="dead").set(dead)
