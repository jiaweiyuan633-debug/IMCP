package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.ai.dto.KnowledgeBaseSaveRequest;
import com.example.admin.module.ai.dto.KnowledgeDocSaveRequest;
import com.example.admin.module.ai.dto.KnowledgeQuery;
import com.example.admin.module.ai.entity.AiKnowledgeBaseDO;
import com.example.admin.module.ai.entity.AiKnowledgeDocDO;
import com.example.admin.module.ai.mapper.AiKnowledgeBaseMapper;
import com.example.admin.module.ai.mapper.AiKnowledgeDocMapper;
import com.example.admin.module.ai.vo.KnowledgeBaseVo;
import com.example.admin.module.ai.vo.KnowledgeDocVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG 知识库：知识库/文档 CRUD + 检索。文档写入后同步到向量存储（Milvus 或 MySQL 全文回退）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final int ENABLED = 1;
    private static final int DEFAULT_TOP_K = 5;

    private final AiKnowledgeBaseMapper baseMapper;
    private final AiKnowledgeDocMapper docMapper;
    private final KnowledgeVectorStore vectorStore;

    // ---------- 知识库 ----------

    public PageResult<KnowledgeBaseVo> pageBase(KnowledgeQuery query) {
        Page<AiKnowledgeBaseDO> page = new Page<>(query.getPageNum(), query.getPageSize(), false);
        LambdaQueryWrapper<AiKnowledgeBaseDO> wrapper = new LambdaQueryWrapper<AiKnowledgeBaseDO>()
                .like(StringUtils.hasText(query.getName()), AiKnowledgeBaseDO::getName, query.getName())
                .orderByDesc(AiKnowledgeBaseDO::getId);
        IPage<AiKnowledgeBaseDO> result = baseMapper.selectPage(page, wrapper);
        page.setTotal(baseMapper.selectCount(wrapper));
        return PageResult.of(result, result.getRecords().stream().map(b -> toBaseVo(b)).toList());
    }

    public Long createBase(KnowledgeBaseSaveRequest request) {
        AiKnowledgeBaseDO base = new AiKnowledgeBaseDO();
        base.setTenantId(TenantContext.getTenantId());
        base.setName(request.getName());
        base.setDescription(request.getDescription());
        base.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        baseMapper.insert(base);
        return base.getId();
    }

    public void updateBase(KnowledgeBaseSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "知识库 ID 不能为空");
        }
        if (baseMapper.selectById(request.getId()) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        AiKnowledgeBaseDO base = new AiKnowledgeBaseDO();
        base.setId(request.getId());
        base.setName(request.getName());
        base.setDescription(request.getDescription());
        base.setStatus(request.getStatus());
        baseMapper.updateById(base);
    }

    public void deleteBase(Long id) {
        baseMapper.deleteById(id);
        vectorStore.deleteByBase(TenantContext.getTenantId(), id);
    }

    // ---------- 文档 ----------

    public PageResult<KnowledgeDocVo> pageDoc(KnowledgeQuery query) {
        Page<AiKnowledgeDocDO> page = new Page<>(query.getPageNum(), query.getPageSize(), false);
        LambdaQueryWrapper<AiKnowledgeDocDO> wrapper = new LambdaQueryWrapper<AiKnowledgeDocDO>()
                .eq(query.getBaseId() != null, AiKnowledgeDocDO::getBaseId, query.getBaseId())
                .eq(StringUtils.hasText(query.getName()), AiKnowledgeDocDO::getTitle, query.getName())
                .orderByDesc(AiKnowledgeDocDO::getId);
        IPage<AiKnowledgeDocDO> result = docMapper.selectPage(page, wrapper);
        page.setTotal(docMapper.selectCount(wrapper));
        return PageResult.of(result, result.getRecords().stream().map(d -> toDocVo(d)).toList());
    }

    public Long createDoc(KnowledgeDocSaveRequest request) {
        AiKnowledgeDocDO doc = toDocEntity(request);
        doc.setTenantId(TenantContext.getTenantId());
        docMapper.insert(doc);
        vectorStore.upsert(TenantContext.getTenantId(), doc);
        return doc.getId();
    }

    public void updateDoc(KnowledgeDocSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档 ID 不能为空");
        }
        if (docMapper.selectById(request.getId()) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        AiKnowledgeDocDO doc = toDocEntity(request);
        docMapper.updateById(doc);
        vectorStore.upsert(TenantContext.getTenantId(), docMapper.selectById(request.getId()));
    }

    public void deleteDoc(Long id) {
        docMapper.deleteById(id);
        vectorStore.deleteByDoc(TenantContext.getTenantId(), id);
    }

    // ---------- 检索 ----------

    public List<String> search(Long baseId, String query, Integer topK) {
        return vectorStore.search(TenantContext.getTenantId(), baseId, query,
                topK == null ? DEFAULT_TOP_K : topK);
    }

    /** 检索知识库并拼装为可注入 Prompt 的上下文片段。 */
    public String buildContext(Long baseId, String query, Integer topK) {
        List<String> hits = search(baseId, query, topK);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("以下为知识库检索到的参考资料：\n");
        for (int i = 0; i < hits.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(hits.get(i)).append('\n');
        }
        sb.append("请优先依据以上资料回答用户问题；资料不足以作答时请如实说明，不要编造知识库中不存在的信息。");
        return sb.toString();
    }

    private AiKnowledgeDocDO toDocEntity(KnowledgeDocSaveRequest request) {
        AiKnowledgeDocDO doc = new AiKnowledgeDocDO();
        doc.setId(request.getId());
        doc.setBaseId(request.getBaseId());
        doc.setTitle(request.getTitle());
        doc.setContent(request.getContent());
        doc.setChunkIndex(0);
        doc.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        return doc;
    }

    private KnowledgeBaseVo toBaseVo(AiKnowledgeBaseDO base) {
        return KnowledgeBaseVo.builder()
                .id(base.getId())
                .name(base.getName())
                .description(base.getDescription())
                .status(base.getStatus())
                .createdAt(base.getCreatedAt())
                .build();
    }

    private KnowledgeDocVo toDocVo(AiKnowledgeDocDO doc) {
        return KnowledgeDocVo.builder()
                .id(doc.getId())
                .baseId(doc.getBaseId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .chunkIndex(doc.getChunkIndex())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
