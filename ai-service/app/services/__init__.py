"""AI algorithm services."""

from app.services.keyword_extract import KeywordExtractService
from app.services.text_summary import TextSummaryService

SERVICE_REGISTRY: dict[str, object] = {
    "text_summary": TextSummaryService(),
    "keyword_extract": KeywordExtractService(),
}


def get_service(biz_type: str) -> object:
    service = SERVICE_REGISTRY.get(biz_type)
    if service is None:
        raise KeyError(f"unsupported biz_type: {biz_type}")
    return service
