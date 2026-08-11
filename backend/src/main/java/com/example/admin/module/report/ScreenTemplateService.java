package com.example.admin.module.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.report.dto.ScreenTemplateSaveRequest;
import com.example.admin.module.report.entity.ScreenTemplateDO;
import com.example.admin.module.report.mapper.ScreenTemplateMapper;
import com.example.admin.module.report.vo.ScreenTemplateVo;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据大屏模板服务：内置模板全租户可见（tenant_id IS NULL，builtin=1），
 * 用户自定义模板租户隔离（tenant_id=当前租户，builtin=0）。
 * 内置模板不可删除/直接覆盖，仅可另存为新模板（前端「另存为」走新增）。
 */
@Service
@RequiredArgsConstructor
public class ScreenTemplateService {

    private static final int ENABLED = 1;

    private final ScreenTemplateMapper screenTemplateMapper;

    public List<ScreenTemplateVo> list() {
        Long tenantId = TenantContext.getTenantId();
        List<ScreenTemplateDO> rows = screenTemplateMapper.selectList(
                new LambdaQueryWrapper<ScreenTemplateDO>()
                        .eq(ScreenTemplateDO::getStatus, ENABLED)
                        .and(w -> w.eq(ScreenTemplateDO::getTenantId, tenantId)
                                .or().isNull(ScreenTemplateDO::getTenantId))
                        .orderByDesc(ScreenTemplateDO::getBuiltin)
                        .orderByAsc(ScreenTemplateDO::getId));
        return rows.stream().map(this::toVo).toList();
    }

    public ScreenTemplateVo detail(Long id) {
        return toVo(requireAccessible(id));
    }

    public Long create(ScreenTemplateSaveRequest request) {
        Long tenantId = TenantContext.getTenantId();
        String code = StringUtils.hasText(request.getCode())
                ? request.getCode().trim()
                : "tpl-" + System.currentTimeMillis();
        checkCodeUnique(tenantId, code, null);
        ScreenTemplateDO template = new ScreenTemplateDO();
        template.setTenantId(tenantId);
        template.setName(request.getName().trim());
        template.setCode(code);
        template.setCategory(request.getCategory());
        template.setTheme(StringUtils.hasText(request.getTheme()) ? request.getTheme() : "dark");
        template.setLayout(request.getLayout());
        template.setRemark(request.getRemark());
        template.setBuiltin(0);
        template.setStatus(ENABLED);
        template.setVersion(0);
        // tryGetUserId：无认证上下文（如系统/定时场景）时为 null，列可空
        template.setCreatedBy(SecurityUtils.tryGetUserId());
        try {
            screenTemplateMapper.insert(template);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.SCREEN_TEMPLATE_CODE_EXISTS);
        }
        return template.getId();
    }

    public void update(ScreenTemplateSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        ScreenTemplateDO existing = requireEditable(request.getId());
        existing.setName(request.getName().trim());
        existing.setCategory(request.getCategory());
        existing.setTheme(StringUtils.hasText(request.getTheme()) ? request.getTheme() : existing.getTheme());
        existing.setLayout(request.getLayout());
        existing.setRemark(request.getRemark());
        if (StringUtils.hasText(request.getCode()) && !request.getCode().equals(existing.getCode())) {
            checkCodeUnique(existing.getTenantId(), request.getCode().trim(), existing.getId());
            existing.setCode(request.getCode().trim());
        }
        screenTemplateMapper.updateById(existing);
    }

    public void delete(Long id) {
        screenTemplateMapper.deleteById(requireEditable(id).getId());
    }

    /** 内置模板仅可另存为新模板：前端对内置模板展示「另存为」，不可直接覆盖。 */
    private ScreenTemplateDO requireEditable(Long id) {
        ScreenTemplateDO existing = requireAccessible(id);
        if (existing.getBuiltin() != null && existing.getBuiltin() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!existing.getTenantId().equals(TenantContext.getTenantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return existing;
    }

    private ScreenTemplateDO requireAccessible(Long id) {
        ScreenTemplateDO existing = screenTemplateMapper.selectById(id);
        if (existing == null || existing.getDeleted() != null && existing.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        Long tenantId = TenantContext.getTenantId();
        if (existing.getTenantId() != null && !existing.getTenantId().equals(tenantId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return existing;
    }

    private void checkCodeUnique(Long tenantId, String code, Long excludeId) {
        Long count = screenTemplateMapper.selectCount(
                new LambdaQueryWrapper<ScreenTemplateDO>()
                        .eq(ScreenTemplateDO::getTenantId, tenantId)
                        .eq(ScreenTemplateDO::getCode, code)
                        .ne(excludeId != null, ScreenTemplateDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.SCREEN_TEMPLATE_CODE_EXISTS);
        }
    }

    private ScreenTemplateVo toVo(ScreenTemplateDO template) {
        return ScreenTemplateVo.builder()
                .id(template.getId())
                .name(template.getName())
                .code(template.getCode())
                .category(template.getCategory())
                .theme(template.getTheme())
                .layout(template.getLayout())
                .remark(template.getRemark())
                .builtin(template.getBuiltin() != null && template.getBuiltin() == 1)
                .createdAt(template.getCreatedAt())
                .build();
    }
}
