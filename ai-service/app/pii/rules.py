"""PII 检测规则定义。

规则按列表顺序即优先级排序：重叠命中同一区间时，优先保留更靠前的规则。
所有数字类规则均使用 ``(?<!\\d)`` / ``(?!\\d)`` 边界约束，避免把长数字
（如 18 位身份证号）内部的连续子串误判为手机号 / 银行卡号。
"""

from dataclasses import dataclass
import re


@dataclass(frozen=True)
class Rule:
    """一条 PII 检测规则。"""

    kind: str
    pattern: re.Pattern[str]


# 中国大陆手机号：1[3-9] 开头共 11 位，前后必须非数字。
# 身份证号等更长数字内部的 11 位子串因前后相邻数字，无法满足边界约束，不会误命中。
PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")

# 身份证号：18 位（末位可为数字或 X/x）或 15 位，前后必须非数字。
ID_CARD = re.compile(r"(?<!\d)(?:\d{17}[\dXx]|\d{15})(?!\d)")

# 邮箱：本地部分 + @ + 域名，域名需含至少一级子域。
EMAIL = re.compile(
    r"(?<![A-Za-z0-9_])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+(?![A-Za-z0-9_])"
)

# 银行卡号：16-19 位连续数字，前后必须非数字。
BANK_CARD = re.compile(r"(?<!\d)\d{16,19}(?!\d)")

# IPv4 地址：每段 0-255，前后必须非数字。
IP_V4 = re.compile(
    r"(?<!\d)(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}"
    r"(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?!\d)"
)

# 信用卡号：可选规则，复用银行卡号的 16-19 位范围；
# 与 bank_card 完全同区间时，因优先级靠后而被合并逻辑剔除。
CREDIT_CARD = re.compile(r"(?<!\d)\d{16,19}(?!\d)")

# 护照号：P 开头共 8 位，或 1 个大写字母 + 8 位数字。
PASSPORT = re.compile(r"(?<![A-Za-z0-9])(?:P[0-9A-Z]{7}|[A-Z]\d{8})(?![A-Za-z0-9])")


PII_RULES: list[Rule] = [
    Rule("phone", PHONE),
    Rule("id_card", ID_CARD),
    Rule("email", EMAIL),
    Rule("bank_card", BANK_CARD),
    Rule("ip", IP_V4),
    Rule("credit_card", CREDIT_CARD),
    Rule("passport", PASSPORT),
]

# 所有规则类型，按 PII_RULES 中的顺序（即优先级顺序）。
ALL_KINDS: tuple[str, ...] = tuple(rule.kind for rule in PII_RULES)
