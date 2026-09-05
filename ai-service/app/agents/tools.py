"""智能体工具层：可插拔工具抽象、注册表与内置工具（计算器/回显/知识库检索）。

设计要点：
- ``AgentTool`` 是工具抽象基类：``name``/``description`` 用于生成给模型的函数声明，
  ``execute`` 是异步执行入口，统一接收 ``dict`` 参数、返回 ``str`` 文本。
- ``ToolRegistry`` 提供注册、汇总（供引擎生成 tools_schema）与按名调用；
  未注册的工具调用抛 ``KeyError``，由上层引擎捕获并转为文本。
- 内置 ``CalculatorTool`` 对表达式做 AST 白名单校验，杜绝注入：只放行四则运算、
  括号与正负号对应的节点，并在受限命名空间内求值（禁用所有内建函数）。
"""

from __future__ import annotations

import ast
from collections.abc import Callable

# 计算器允许的 AST 节点白名单：仅四则运算、括号与正负号
_ALLOWED_AST_NODES = (
    ast.Expression,
    ast.BinOp,
    ast.UnaryOp,
    ast.Constant,
    ast.Add,
    ast.Sub,
    ast.Mult,
    ast.Div,
    ast.USub,
)


def _is_safe_expression(expr: str) -> bool:
    """校验表达式只含四则运算：语法合法且 AST 节点全部落在白名单内。"""
    try:
        tree = ast.parse(expr, mode="eval")
    except SyntaxError:
        return False
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant):
            # 仅允许数字常量，排除字符串/布尔等（布尔也是 int，需按类型严格判断）
            if type(node.value) not in (int, float):
                return False
        elif not isinstance(node, _ALLOWED_AST_NODES):
            return False
    return True


class AgentTool:
    """智能体可调用工具的抽象基类。"""

    name: str
    description: str

    async def execute(self, args: dict) -> str:
        """执行工具并返回文本结果，参数统一为 ``dict``。"""
        raise NotImplementedError


class CalculatorTool(AgentTool):
    """四则运算计算器：仅接受 ``+ - * /``、括号与数字组成的表达式。"""

    def __init__(self, name: str = "calculator") -> None:
        self.name = name
        self.description = "执行四则运算表达式，支持 + - * / 与括号（如 2+3*4）"

    async def execute(self, args: dict) -> str:
        expr = str(args.get("expression", "")).strip()
        if not expr or not _is_safe_expression(expr):
            return "非法表达式"
        try:
            # 在受限命名空间内求值，禁止访问内建函数与任意对象
            result = eval(expr, {"__builtins__": {}}, {})
        except ZeroDivisionError:
            return "除数不能为 0"
        except Exception:  # noqa: BLE001 —— 求值兜底，一律按非法表达式处理
            return "非法表达式"
        return f"{expr} = {result}"


class EchoTool(AgentTool):
    """回显工具：原样返回传入的 ``text`` 参数。"""

    def __init__(self, name: str = "echo") -> None:
        self.name = name
        self.description = "原样回显输入的 text 文本"

    async def execute(self, args: dict) -> str:
        return str(args.get("text", ""))


class KBRetrieverTool(AgentTool):
    """知识库检索工具：委托外部可调用对象完成检索；未配置时返回提示文本。"""

    def __init__(
        self,
        name: str = "kb_search",
        fn: Callable[[str], str] | None = None,
    ) -> None:
        self.name = name
        self.description = "在知识库中检索关键词并返回相关文本"
        self._fn = fn

    async def execute(self, args: dict) -> str:
        query = str(args.get("query", ""))
        if self._fn is None:
            return "知识库检索未配置"
        return str(self._fn(query))


class ToolRegistry:
    """工具注册表：注册、汇总（生成模型函数声明）与按名调用。"""

    def __init__(self, tools: list[AgentTool] | None = None) -> None:
        self._tools: dict[str, AgentTool] = {}
        for tool in tools or []:
            self.register(tool)

    def register(self, tool: AgentTool) -> None:
        """注册一个工具；同名注册会覆盖。"""
        self._tools[tool.name] = tool

    def names(self) -> list[str]:
        """返回已注册工具名列表。"""
        return list(self._tools)

    def summaries(self) -> list[dict]:
        """返回 ``[{"name", "description"}]`` 供引擎生成函数声明。"""
        return [
            {"name": tool.name, "description": tool.description}
            for tool in self._tools.values()
        ]

    async def call(self, name: str, args: dict) -> str:
        """按名调用工具；未注册时抛 ``KeyError``。"""
        tool = self._tools.get(name)
        if tool is None:
            raise KeyError(name)
        return await tool.execute(args)


def build_default_registry(kb_retriever: Callable[[str], str] | None = None) -> ToolRegistry:
    """构建默认注册表：计算器 + 回显 + 知识库检索（后者可注入外部检索函数）。"""
    return ToolRegistry([
        CalculatorTool(),
        EchoTool(),
        KBRetrieverTool(fn=kb_retriever),
    ])
