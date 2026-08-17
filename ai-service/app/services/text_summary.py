import re


class TextSummaryService:

    async def run(self, params: dict) -> dict:
        # 批次3（R4-1.49）：移除 force_fail/delay_seconds 演示后门——任意持令牌者可
        # 强制任务失败刷死信、用大 delay 占满 worker 池造成排队 DoS
        content = str(params.get("content", "")).strip()
        if not content:
            raise ValueError("content is required")

        max_length = max(int(params.get("max_length", 200)), 20)
        sentences = re.split(r"(?<=[。！？!?；;])", content)
        summary = ""
        for sentence in sentences:
            if len(summary) + len(sentence) > max_length:
                break
            summary += sentence
        if not summary:
            summary = content[:max_length]

        return {
            "summary": summary,
            "original_length": len(content),
            "summary_length": len(summary),
        }
