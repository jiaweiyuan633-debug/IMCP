import re


class TextSummaryService:

    async def run(self, params: dict) -> dict:
        content = str(params.get("content", "")).strip()
        if not content:
            raise ValueError("content is required")
        if params.get("force_fail"):
            raise RuntimeError("forced failure for demo")

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
