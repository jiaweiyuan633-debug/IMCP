import re
from collections import Counter


class KeywordExtractService:

    async def run(self, params: dict) -> dict:
        # force_fail/delay_seconds 演示后门已移除，不再响应此类参数（同 text_summary）
        content = str(params.get("content", "")).strip()
        if not content:
            raise ValueError("content is required")

        top_n = max(int(params.get("top_n", 10)), 1)
        words = re.findall(r"[a-zA-Z0-9_]+", content.lower())
        han_words = re.findall(r"[\u4e00-\u9fff]+", content)
        counter = Counter(words + han_words)
        keywords = [
            {"word": word, "score": score}
            for word, score in counter.most_common(top_n)
        ]
        return {
            "keywords": keywords,
            "total_tokens": sum(counter.values()),
        }
