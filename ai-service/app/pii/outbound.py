"""出域文本 PII 脱敏的共享出口与开关判定。

所有「把文本发给外部 LLM / Embedding 提供方」的调用点统一收敛到本模块判定
并执行脱敏，避免各路径各自实现、遗漏个别出域口（chat/stream/embed/vector
upsert、RAG 入库与检索、agent 提示词与工具结果等）：

- ``should_mask_outbound(settings, provider, requested)``：服务端强制开关
  （settings.pii_mask_required）开启时，调用方（mask_pii=false 等）无法关闭
  脱敏；mock 提供方在进程内完成、无跨进程出域，可豁免强制（本地联调/测试）。
- ``mask_outbound_messages`` / ``mask_outbound_texts``：对消息 / 文本列表执行
  掩码。detect/mask 为同步 CPU 工作，调用方按需放入线程池（见 app.core.threads）。
"""

from __future__ import annotations

from typing import Any

from app.pii.masker import mask


def should_mask_outbound(settings: Any, provider: Any, requested: bool) -> bool:
    """判定一次出域调用是否需要先脱敏（见模块 docstring）。"""
    if bool(getattr(settings, "pii_mask_required", False)):
        return getattr(provider, "name", "") != "mock"
    return bool(requested)


def mask_outbound_messages(messages: list[dict], mask_char: str) -> list[dict]:
    """对消息中的 user 文本执行 PII 脱敏，返回新列表。

    同步函数，供线程池调用；content 为 None/空时原样保留。
    """
    result: list[dict] = []
    for message in messages:
        if message.get("role") == "user" and message.get("content"):
            message = {**message, "content": mask(str(message["content"]), mask_char)}
        result.append(message)
    return result


def mask_outbound_texts(texts: list[str], mask_char: str) -> list[str]:
    """对一批待向量化/待发送的文本执行 PII 脱敏。"""
    return [mask(str(text), mask_char) for text in texts]
