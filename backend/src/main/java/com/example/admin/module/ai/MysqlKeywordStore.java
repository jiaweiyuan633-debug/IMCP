package com.example.admin.module.ai;

import com.example.admin.module.ai.entity.AiKnowledgeDocDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MySQL ngram 全文检索知识库（默认实现，零外部依赖），检索逻辑复用 {@link MysqlFulltextSearcher}。
 * <p>与 {@link MilvusKnowledgeStore} 通过 app.milvus.enabled 属性互斥：
 * 开启（true）用 Milvus 向量检索，未开启/缺省（false）用本实现。
 * 不能再用 @ConditionalOnMissingBean——组件扫描顺序下它会在评估时发现自身定义而自我排除。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.milvus.enabled", havingValue = "false", matchIfMissing = true)
public class MysqlKeywordStore implements KnowledgeVectorStore {

    private final MysqlFulltextSearcher searcher;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void upsert(Long tenantId, AiKnowledgeDocDO doc) {
        // 文档已落库，MySQL 全文索引自动可见，无需额外写入
    }

    @Override
    public void deleteByDoc(Long tenantId, Long docId) {
        // 逻辑删除后索引随行消失，无需额外清理
    }

    @Override
    public void deleteByBase(Long tenantId, Long baseId) {
        // 逻辑删除后索引随行消失，无需额外清理
    }

    @Override
    public List<String> search(Long tenantId, Long baseId, String query, int topK) {
        return searcher.search(tenantId, baseId, query, topK);
    }
}
