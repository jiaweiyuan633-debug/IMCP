"""智能体层测试：工具注册表、内置工具（计算器/回显/知识库检索）与 agentic loop。"""

import pytest

from app.agents.engine import AgentEngine
from app.agents.tools import (
    CalculatorTool,
    EchoTool,
    KBRetrieverTool,
    ToolRegistry,
    build_default_registry,
)
from app.llm.base import LLMResponse, ToolCall
from app.llm.mock import MockProvider


@pytest.mark.asyncio
async def test_calculator_safe_eval() -> None:
    """合法四则表达式应按运算符优先级正确求值。"""
    tool = CalculatorTool()
    result = await tool.execute({"expression": "2+3*4"})
    assert result == "2+3*4 = 14"


@pytest.mark.asyncio
async def test_calculator_illegal_expression() -> None:
    """非法表达式应返回提示文本而非抛异常。"""
    tool = CalculatorTool()
    result = await tool.execute({"expression": "1+"})
    assert result == "非法表达式"


@pytest.mark.asyncio
async def test_calculator_division_by_zero() -> None:
    """除零应返回明确错误提示。"""
    tool = CalculatorTool()
    result = await tool.execute({"expression": "1/0"})
    assert result == "除数不能为 0"


@pytest.mark.asyncio
async def test_calculator_rejects_injection() -> None:
    """含函数/变量调用的表达式（注入尝试）应被拒绝。"""
    tool = CalculatorTool()
    assert await tool.execute({"expression": "__import__('os').system('dir')"}) == "非法表达式"


@pytest.mark.asyncio
async def test_echo_tool() -> None:
    """回显工具：返回 text 参数，缺省为空串。"""
    tool = EchoTool()
    assert await tool.execute({"text": "你好"}) == "你好"
    assert await tool.execute({}) == ""


@pytest.mark.asyncio
async def test_full_loop_with_tool() -> None:
    """完整闭环：calculate 触发 calculator，最终答复包含结果、steps 含该工具。"""
    registry = build_default_registry()
    engine = AgentEngine(MockProvider(), registry)
    result = await engine.run({"user_prompt": "calculate 1+2"})
    assert "3" in result["final"]
    assert result["steps"][0]["tool"] == "calculator"
    assert result["steps_used"] >= 1
    assert result["steps_used"] == len(result["steps"])


@pytest.mark.asyncio
async def test_no_tool_direct_final() -> None:
    """无工具需求的输入应直接返回最终文本且不产生任何步骤。"""
    registry = build_default_registry()
    engine = AgentEngine(MockProvider(), registry)
    result = await engine.run({"user_prompt": "echo hi"})
    assert result["final"] == "hi"
    assert result["steps"] == []
    assert result["steps_used"] == 0


@pytest.mark.asyncio
async def test_max_steps_truncation() -> None:
    """模型持续请求工具时，步数达上限应强制兜底输出并终止。"""

    class _AlwaysToolProvider:
        name = "stub"

        async def chat_with_tools(self, messages, tools, model=None, temperature=None):
            return LLMResponse(
                content=None,
                tool_calls=[ToolCall("calculator", {"expression": "1+1"})],
            )

    engine = AgentEngine(_AlwaysToolProvider(), build_default_registry())
    result = await engine.run({"user_prompt": "calculate 1+1", "max_steps": 1})
    assert result["steps_used"] <= 1
    assert result["final"] == "已达最大步数，未得到最终答复"


@pytest.mark.asyncio
async def test_kb_search_not_configured() -> None:
    """未注入知识库检索函数时返回提示文本。"""
    registry = build_default_registry()
    result = await registry.call("kb_search", {"query": "管理"})
    assert result == "知识库检索未配置"


@pytest.mark.asyncio
async def test_kb_search_configured() -> None:
    """注入知识库检索函数后按 query 委托调用。"""
    registry = build_default_registry(kb_retriever=lambda q: f"命中：{q}")
    result = await registry.call("kb_search", {"query": "权限"})
    assert result == "命中：权限"


@pytest.mark.asyncio
async def test_unregistered_tool_raises_key_error() -> None:
    """调用未注册的工具应抛 KeyError。"""
    registry = build_default_registry()
    with pytest.raises(KeyError):
        await registry.call("no_such_tool", {})


def test_registry_register_names_and_summaries() -> None:
    """注册表：register/names/summaries 行为正确，汇总含 name 与 description。"""
    registry = ToolRegistry([EchoTool()])
    registry.register(KBRetrieverTool())
    assert set(registry.names()) == {"echo", "kb_search"}
    summaries = registry.summaries()
    assert all(set(item) == {"name", "description"} for item in summaries)
    assert {item["name"] for item in summaries} == {"echo", "kb_search"}


def test_build_default_registry_has_all_tools() -> None:
    """默认注册表应包含 calculator、echo、kb_search。"""
    registry = build_default_registry()
    assert set(registry.names()) == {"calculator", "echo", "kb_search"}
