"""AI 任务执行器注册表：biz_type → 服务实例。

服务按运行时上下文（ServiceContext）装配：未显式注入时由任务管理器以
默认上下文（Mock 提供方 + Redis 向量存储）惰性构建。
"""

from app.services.agent_service import AgentService
from app.services.base import BaseTaskService
from app.services.context import ServiceContext
from app.services.doc_parse import DocParseService
from app.services.embedding import EmbeddingService
from app.services.keyword_extract import KeywordExtractService
from app.services.llm_chat import LlmChatService
from app.services.ml_service import MlClassifyService, MlClusterService
from app.services.ocr_service import OcrService
from app.services.pii_service import PiiMaskService
from app.services.rag_service import RagIngestService, RagRetrieveService
from app.services.schedule_service import ScheduleRegisterService
from app.services.text_summary import TextSummaryService

__all__ = [
    "AgentService",
    "BaseTaskService",
    "DocParseService",
    "EmbeddingService",
    "KeywordExtractService",
    "LlmChatService",
    "MlClassifyService",
    "MlClusterService",
    "OcrService",
    "PiiMaskService",
    "RagIngestService",
    "RagRetrieveService",
    "ScheduleRegisterService",
    "ServiceContext",
    "TextSummaryService",
    "build_services",
]


def build_services(context: ServiceContext) -> dict[str, BaseTaskService]:
    """装配全部任务执行器（biz_type → 服务）。"""
    return {
        "text_summary": TextSummaryService(),
        "keyword_extract": KeywordExtractService(),
        "llm_chat": LlmChatService(context),
        "embedding": EmbeddingService(context),
        "rag_ingest": RagIngestService(context),
        "rag_retrieve": RagRetrieveService(context),
        "doc_parse": DocParseService(context),
        "ml_classify": MlClassifyService(context),
        "ml_cluster": MlClusterService(context),
        "ocr": OcrService(context),
        "agent": AgentService(context),
        "pii_mask": PiiMaskService(context),
        "schedule_register": ScheduleRegisterService(context),
    }
