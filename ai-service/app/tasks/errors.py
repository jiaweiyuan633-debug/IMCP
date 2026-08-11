"""任务执行异常分类：可重试 / 不可重试。

- ``RetryableError``：网络抖动、上游 5xx 等，按退避重试。
- ``NonRetryableError``：参数非法、认证失败等，重试无意义，直接置为失败并进死信。
- 任务管理器对 ``asyncio.TimeoutError`` 一律按不可重试处理（重试只会重复浪费超时窗口）。
"""


class RetryableError(RuntimeError):
    """可重试的上游/瞬时异常。"""


class NonRetryableError(RuntimeError):
    """不可重试的确定性异常（参数/配置/认证问题）。"""
