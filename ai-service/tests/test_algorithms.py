import pytest

from app.services.keyword_extract import KeywordExtractService
from app.services.text_summary import TextSummaryService


@pytest.mark.asyncio
async def test_text_summary() -> None:
    service = TextSummaryService()
    result = await service.run({
        "content": "第一句话。第二句话。第三句话。",
        "max_length": 10,
    })
    assert result["summary"]
    assert result["summary_length"] <= 20


@pytest.mark.asyncio
async def test_keyword_extract() -> None:
    service = KeywordExtractService()
    result = await service.run({
        "content": "管理系统 权限 管理系统 认证 权限",
        "top_n": 2,
    })
    assert result["keywords"]
    assert result["keywords"][0]["word"] in ("管理系统", "权限")

