"""LLM 重试（R4-1.31）：非任务路径（同步 /chat 与 Agent 引擎）对可重试异常退避重试。

覆盖：
- retry_llm_call：可重试异常重试至成功 / 耗尽上抛、配置错误立即上抛；
- /chat 接口：provider 首次失败时重试成功后返回 200；
- Agent 引擎：单次模型调用失败重试后整轮成功。
"""

import fakeredis.aioredis
import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.agents.engine import AgentEngine
from app.agents.tools import build_default_registry
from app.core.config import settings
from app.llm.base import LLMConfigError, LLMError, LLMResponse
from app.llm.retry import retry_llm_call

AUTH = {"Authorization": f"Bearer {settings.auth_token}"}

main_module.Redis = fakeredis.aioredis.FakeRedis


# ---------- retry_llm_call 单测 ----------

class _CountingOp:
    """可编程 LLM 调用：前 fail_times 次抛可重试异常，随后成功。"""

    def __init__(self, fail_times: int = 1, exc: Exception = LLMError("boom")) -> None:
        self.fail_times = fail_times
        self.exc = exc
        self.calls = 0

    async def __call__(self, tag: str = "x") -> str:
        self.calls += 1
        if self.calls <= self.fail_times:
            raise self.exc
        return f"ok-{tag}"


@pytest.mark.asyncio
async def test_retry_succeeds_after_transient_failure() -> None:
    op = _CountingOp(fail_times=1)

    result = await retry_llm_call(op, "a", max_attempts=3, base_delay=0.0)

    assert result == "ok-a"
    assert op.calls == 2  # 首次失败 + 重试成功


@pytest.mark.asyncio
async def test_retry_exhausted_rethrows_last_error() -> None:
    op = _CountingOp(fail_times=99)

    with pytest.raises(LLMError):
        await retry_llm_call(op, "a", max_attempts=3, base_delay=0.0)

    assert op.calls == 3  # 恰重试 max_attempts 次后放弃


@pytest.mark.asyncio
async def test_config_error_not_retried() -> None:
    op = _CountingOp(fail_times=99, exc=LLMConfigError("未配置模型"))

    with pytest.raises(LLMConfigError):
        await retry_llm_call(op, "a", max_attempts=3, base_delay=0.0)

    assert op.calls == 1  # 配置错误无重试意义，立即上抛


# ---------- /chat 接口集成 ----------

class FlakyChatProvider:
    """chat 首次抛可重试异常，第二次成功。"""

    name = "flaky"

    def __init__(self) -> None:
        self.calls = 0

    async def chat(self, messages, model=None, temperature=None, max_tokens=None):
        self.calls += 1
        if self.calls == 1:
            raise LLMError("upstream 5xx")
        return "ok-after-retry"


def test_chat_retries_transient_provider_error() -> None:
    with TestClient(main_module.app) as client:
        flaky = FlakyChatProvider()
        client.app.state.providers.register("flaky", flaky)

        response = client.post(
            "/api/v1/chat",
            json={"messages": [{"role": "user", "content": "hi"}], "provider": "flaky"},
            headers=AUTH,
        )

    assert response.status_code == 200
    assert response.json()["content"] == "ok-after-retry"
    assert flaky.calls == 2  # 首次失败自动重试，未把瞬时错误抛给调用方


# ---------- Agent 引擎集成 ----------

class FlakyWithToolsProvider:
    """chat_with_tools 首次抛可重试异常，第二次直接返回最终答复。"""

    name = "flaky-tools"

    def __init__(self) -> None:
        self.calls = 0

    async def chat_with_tools(self, messages, tools, model=None, temperature=None):
        self.calls += 1
        if self.calls == 1:
            raise LLMError("upstream timeout")
        return LLMResponse(content="hi", model=model)


@pytest.mark.asyncio
async def test_agent_engine_retries_model_error() -> None:
    flaky = FlakyWithToolsProvider()
    engine = AgentEngine(flaky, build_default_registry())

    result = await engine.run({"user_prompt": "echo hi"})

    assert result["final"] == "hi"
    assert flaky.calls == 2  # 单次模型调用失败被重试吸收，整轮仍成功
