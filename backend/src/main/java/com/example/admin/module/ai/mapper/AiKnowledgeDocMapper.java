package com.example.admin.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.ai.entity.AiKnowledgeDocDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiKnowledgeDocMapper extends BaseMapper<AiKnowledgeDocDO> {

    /** MySQL ngram 全文检索（手动处理 deleted/status，绕过 @TableLogic）。 */
    @Select("SELECT id, tenant_id, base_id, title, content, chunk_index, status, created_at " +
            "FROM ai_knowledge_doc " +
            "WHERE tenant_id = #{tenantId} AND base_id = #{baseId} AND deleted = 0 AND status = 1 " +
            "AND MATCH(title, content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<AiKnowledgeDocDO> fulltextSearch(@Param("tenantId") Long tenantId,
                                          @Param("baseId") Long baseId,
                                          @Param("query") String query,
                                          @Param("limit") int limit);

    /** LIKE 模糊检索回退（全文索引对过短/无分词查询可能无结果）。 */
    @Select("SELECT id, tenant_id, base_id, title, content, chunk_index, status, created_at " +
            "FROM ai_knowledge_doc " +
            "WHERE tenant_id = #{tenantId} AND base_id = #{baseId} AND deleted = 0 AND status = 1 " +
            "AND (title LIKE CONCAT('%', #{query}, '%') OR content LIKE CONCAT('%', #{query}, '%')) " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<AiKnowledgeDocDO> likeSearch(@Param("tenantId") Long tenantId,
                                      @Param("baseId") Long baseId,
                                      @Param("query") String query,
                                      @Param("limit") int limit);
}
