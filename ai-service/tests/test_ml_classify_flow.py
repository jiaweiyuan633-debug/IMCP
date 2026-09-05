"""P1 回归：ml_classify train 真正执行并落库，predict 能读到模型。

修复前 app/services/ml_service.py 把 async train_model（协程函数）交给
asyncio.to_thread：to_thread 只接收普通可调用对象，传入协程函数得到的是
「永不 await 的协程对象」——训练不执行、模型不落库，任务结果序列化协程抛
TypeError 并被无限 requeue。修复后 CPU 训练在线程池、Redis 写入回事件循环，
经 TaskManager 端到端验证 train 后 predict 成功、结果可 JSON 序列化。
"""

import asyncio
import json

import fakeredis.aioredis
import pytest

from app.core.config import Settings
from app.schemas.task import TaskCreateRequest
from app.tasks.manager import TaskManager

SPORT = ["足球 篮球 比赛 球队", "篮球 运动 比赛 得分"]
TECH = ["芯片 人工智能 编程 软件", "科技 互联网 软件 编程"]
FIN = ["股票 基金 投资 财经", "金融 银行 股市 投资"]


@pytest.mark.asyncio
async def test_ml_classify_train_then_predict_via_task_manager() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(worker_count=1, retry_backoff_seconds=0.01))

    await manager.create_task(TaskCreateRequest(
        task_no="train-1",
        biz_type="ml_classify",
        params={
            "action": "train",
            "name": "demo",
            "labels": ["体育", "体育", "科技", "科技", "财经", "财经"],
            "docs": SPORT + TECH + FIN,
        },
    ))
    train = await _wait_terminal(manager, "train-1")
    assert train.status == "SUCCEEDED"
    # 结果必须是可 JSON 序列化的普通 dict（修复前是协程对象，序列化抛 TypeError）
    json.dumps(train.result)
    assert train.result["name"] == "demo"
    assert train.result["label_count"] == 3
    assert train.result["samples"] == 6

    # predict 任务读到同一份 Redis 模型并正确分类
    await manager.create_task(TaskCreateRequest(
        task_no="predict-1",
        biz_type="ml_classify",
        params={"action": "predict", "name": "demo", "doc": "足球 比赛 球队"},
    ))
    predict = await _wait_terminal(manager, "predict-1")
    assert predict.status == "SUCCEEDED"
    assert predict.result["label"] == "体育"
    json.dumps(predict.result)


@pytest.mark.asyncio
async def test_ml_classify_predict_missing_model_fails_non_retryable() -> None:
    """predict 不存在的模型一次失败（non_retryable），不浪费重试。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(worker_count=1, retry_backoff_seconds=0.01))
    await manager.create_task(TaskCreateRequest(
        task_no="predict-missing",
        biz_type="ml_classify",
        params={"action": "predict", "name": "no-such-model", "doc": "x"},
    ))
    task = await _wait_terminal(manager, "predict-missing")
    assert task.status == "FAILED"
    assert task.reason == "non_retryable"
    assert task.retry_count == 1


@pytest.mark.asyncio
async def test_ml_cluster_succeeds_and_result_serializable() -> None:
    """ml_cluster 经任务管理器执行成功（结果可序列化）。"""
    redis = fakeredis.aioredis.FakeRedis(decode_responses=True)
    manager = TaskManager(redis, Settings(worker_count=1, retry_backoff_seconds=0.01))
    await manager.create_task(TaskCreateRequest(
        task_no="cluster-1",
        biz_type="ml_cluster",
        params={"docs": ["苹果 香蕉 水果", "香蕉 苹果 水果", "股票 基金 投资", "财经 股票 基金"], "k": 2},
    ))
    task = await _wait_terminal(manager, "cluster-1")
    assert task.status == "SUCCEEDED"
    assert task.result["iterations"] >= 1
    json.dumps(task.result)


async def _wait_terminal(manager: TaskManager, task_no: str, attempts: int = 200):
    for _ in range(attempts):
        await asyncio.sleep(0.02)
        task = await manager.get_task(task_no)
        if task is not None and task.status in ("SUCCEEDED", "FAILED"):
            return task
    raise AssertionError(f"任务 {task_no} 未在预期时间内到达终态")
