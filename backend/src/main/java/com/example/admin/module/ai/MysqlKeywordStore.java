package com.example.admin.module.ai;

import com.example.admin.module.ai.entity.AiKnowledgeDocDO;
import com.example.admin.module.ai.mapper.AiKnowledgeDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * MySQL ngram 全文检索知识库（默认实现，零外部依赖）。
 * 命中片段直接取自 doc 的 title/content，供 Prompt 上下文注入。
 * <p>与 {@link MilvusKnowledgeStore} 通过 app.milvus.enabled 属性互斥：
 * 开启（true）用 Milvus 向量检索，未开启/缺省（false）用本实现。
 * 不能再用 @ConditionalOnMissingBean——组件扫描顺序下它会在评估时发现自身定义而自我排除。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.milvus.enabled", havingValue = "false", matchIfMissing = true)
public class MysqlKeywordStore implements KnowledgeVectorStore {

    private final AiKnowledgeDocMapper docMapper;

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
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<AiKnowledgeDocDO> hits = docMapper.fulltextSearch(tenantId, baseId, query, topK);
        if (hits.isEmpty()) {
            hits = docMapper.likeSearch(tenantId, baseId, query, topK);
        }
        return hits.stream().map(this::format).toList();
    }

    private String format(AiKnowledgeDocDO doc) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(doc.getTitle())) {
            sb.append("标题：").append(doc.getTitle()).append('\n');
        }
        if (StringUtils.hasText(doc.getContent())) {
            sb.append("内容：").append(doc.getContent());
        }
        return sb.toString();
    }
}
