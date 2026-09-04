package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.module.ai.entity.AiKnowledgeDocDO;

import java.util.List;

/**
 * 知识向量存储抽象：文档写入/删除/检索。
 * 默认走 {@link MysqlKeywordStore}（MySQL ngram 全文检索，零依赖）；
 * 配置 app.milvus.enabled=true 时替换为 {@link MilvusKnowledgeStore}（向量检索）。
 */
public interface KnowledgeVectorStore {

    /** 是否启用（用于诊断/日志展示，不改变注入行为）。 */
    default boolean isEnabled() {
        return false;
    }

    /** 写入/更新一个文档块。 */
    void upsert(Long tenantId, AiKnowledgeDocDO doc);

    /** 删除单个文档块。 */
    void deleteByDoc(Long tenantId, Long docId);

    /** 删除某知识库下全部文档块。 */
    void deleteByBase(Long tenantId, Long baseId);

    /** 检索 TOP K 命中片段（已拼好可注入 Prompt 的文本）。 */
    List<String> search(Long tenantId, Long baseId, String query, int topK);
}
