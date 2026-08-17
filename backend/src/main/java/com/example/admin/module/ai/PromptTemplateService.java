package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.common.UniqueKeyRelease;
import com.example.admin.module.ai.dto.PromptQuery;
import com.example.admin.module.ai.dto.PromptSaveRequest;
import com.example.admin.module.ai.entity.AiPromptTemplateDO;
import com.example.admin.module.ai.mapper.AiPromptTemplateMapper;
import com.example.admin.module.ai.vo.PromptVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Prompt 模板：CRUD + 占位符渲染（支持 {var} 与 ${var} 两种写法）。
 */
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private static final int ENABLED = 1;

    private final AiPromptTemplateMapper templateMapper;

    public PageResult<PromptVo> page(PromptQuery query) {
        Page<AiPromptTemplateDO> page = new Page<>(query.getPageNum(), query.getPageSize(), false);
        LambdaQueryWrapper<AiPromptTemplateDO> wrapper = new LambdaQueryWrapper<AiPromptTemplateDO>()
                .like(StringUtils.hasText(query.getName()), AiPromptTemplateDO::getName, query.getName())
                .eq(query.getStatus() != null, AiPromptTemplateDO::getStatus, query.getStatus())
                .orderByAsc(AiPromptTemplateDO::getSort)
                .orderByAsc(AiPromptTemplateDO::getId);
        IPage<AiPromptTemplateDO> result = templateMapper.selectPage(page, wrapper);
        page.setTotal(templateMapper.selectCount(wrapper));
        return PageResult.of(result, result.getRecords().stream().map(t -> toVo(t)).toList());
    }

    public Long create(PromptSaveRequest request) {
        checkCodeUnique(request.getCode(), null);
        AiPromptTemplateDO template = toEntity(request);
        template.setTenantId(TenantContext.getTenantId());
        templateMapper.insert(template);
        return template.getId();
    }

    public void update(PromptSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板 ID 不能为空");
        }
        if (templateMapper.selectById(request.getId()) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        checkCodeUnique(request.getCode(), request.getId());
        templateMapper.updateById(toEntity(request));
    }

    public void delete(Long id) {
        // 批次4（R4-1.50）：逻辑删除 + (tenant_id, code) 唯一键冲突——删除前释放编码唯一键
        AiPromptTemplateDO template = templateMapper.selectById(id);
        if (template != null) {
            template.setCode(UniqueKeyRelease.releaseCode(template.getCode()));
            templateMapper.updateById(template);
        }
        templateMapper.deleteById(id);
    }

    /** 渲染模板：按 code 取启用的模板，用参数替换 {var} / ${var} 占位符。 */
    public String render(String code, Map<String, Object> params) {
        AiPromptTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplateDO>()
                .eq(AiPromptTemplateDO::getCode, code)
                .eq(AiPromptTemplateDO::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "Prompt 模板不存在或未启用: " + code);
        }
        String content = template.getContent() == null ? "" : template.getContent();
        if (params == null || params.isEmpty()) {
            return content;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());
            content = content.replace("${" + key + "}", value).replace("{" + key + "}", value);
        }
        return content;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        Long count = templateMapper.selectCount(new LambdaQueryWrapper<AiPromptTemplateDO>()
                .eq(AiPromptTemplateDO::getCode, code)
                .ne(excludeId != null, AiPromptTemplateDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PROMPT_CODE_EXISTS);
        }
    }

    private AiPromptTemplateDO toEntity(PromptSaveRequest request) {
        AiPromptTemplateDO template = new AiPromptTemplateDO();
        template.setId(request.getId());
        template.setCode(request.getCode());
        template.setName(request.getName());
        template.setContent(request.getContent());
        template.setVariables(request.getVariables());
        template.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        template.setSort(request.getSort() == null ? 0 : request.getSort());
        template.setDescription(request.getDescription());
        return template;
    }

    private PromptVo toVo(AiPromptTemplateDO template) {
        return PromptVo.builder()
                .id(template.getId())
                .code(template.getCode())
                .name(template.getName())
                .content(template.getContent())
                .variables(template.getVariables())
                .status(template.getStatus())
                .sort(template.getSort())
                .description(template.getDescription())
                .createdAt(template.getCreatedAt())
                .build();
    }
}
