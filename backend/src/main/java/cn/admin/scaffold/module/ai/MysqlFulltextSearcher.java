package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.module.ai.entity.AiKnowledgeDocDO;
import cn.admin.scaffold.module.ai.mapper.AiKnowledgeDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * MySQL ngram 全文检索片段检索器：默认知识检索实现，也是 Milvus 运行不可用时的降级路径。
 * 命中片段直接取自 doc 的 title/content，供 Prompt 上下文注入。
 */
@Component
@RequiredArgsConstructor
public class MysqlFulltextSearcher {

    private final AiKnowledgeDocMapper docMapper;

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
