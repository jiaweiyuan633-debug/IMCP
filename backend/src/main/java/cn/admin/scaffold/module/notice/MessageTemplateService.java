package cn.admin.scaffold.module.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.notice.dto.MessageTemplateQuery;
import cn.admin.scaffold.module.notice.dto.MessageTemplateSaveRequest;
import cn.admin.scaffold.module.notice.dto.MessageTemplateSendRequest;
import cn.admin.scaffold.module.notice.entity.SysMessageTemplateDO;
import cn.admin.scaffold.module.notice.mapper.SysMessageTemplateMapper;
import cn.admin.scaffold.module.notice.vo.MessageTemplateVo;
import cn.admin.scaffold.module.system.SystemMessageService;
import cn.admin.scaffold.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 消息模板服务：模板 CRUD + 按模板渲染发送。
 *
 * <p>模板与 {@link SystemMessageService} 解耦：模板管"文案与占位符"，发送管"落库与推送"。
 * 渲染后通过 sendWithType 传递内容类型（TEXT/HTML），支持富文本站内信。
 */
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private static final int ENABLED = 1;
    private static final String CONTENT_TYPE_TEXT = "TEXT";

    private final SysMessageTemplateMapper templateMapper;
    private final MessageTemplateRenderer renderer;
    private final SystemMessageService systemMessageService;

    public PageResult<MessageTemplateVo> page(MessageTemplateQuery query) {
        Page<SysMessageTemplateDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysMessageTemplateDO> wrapper = new LambdaQueryWrapper<SysMessageTemplateDO>()
                .like(StringUtils.hasText(query.getTemplateCode()), SysMessageTemplateDO::getTemplateCode, query.getTemplateCode())
                .like(StringUtils.hasText(query.getTemplateName()), SysMessageTemplateDO::getTemplateName, query.getTemplateName())
                .eq(StringUtils.hasText(query.getMessageType()), SysMessageTemplateDO::getMessageType, query.getMessageType())
                .eq(query.getStatus() != null, SysMessageTemplateDO::getStatus, query.getStatus())
                .orderByDesc(SysMessageTemplateDO::getId);
        IPage<SysMessageTemplateDO> result = templateMapper.selectPage(page, wrapper);
        List<MessageTemplateVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public Long create(MessageTemplateSaveRequest request) {
        checkCodeUnique(request.getTemplateCode(), null);
        SysMessageTemplateDO template = toEntity(request);
        template.setCreatedBy(SecurityUtils.tryGetUserId());
        templateMapper.insert(template);
        return template.getId();
    }

    public void update(MessageTemplateSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板 ID 不能为空");
        }
        checkCodeUnique(request.getTemplateCode(), request.getId());
        templateMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer status) {
        SysMessageTemplateDO template = new SysMessageTemplateDO();
        template.setId(id);
        template.setStatus(status);
        templateMapper.updateById(template);
    }

    @Transactional
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }

    /**
     * 按模板渲染并发送：取模板 → 校验启用 → 渲染标题/内容 → 交消息中心落库推送。
     */
    public Long sendByTemplate(MessageTemplateSendRequest request) {
        SysMessageTemplateDO template = selectByCode(request.getTemplateCode());
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (template.getStatus() == null || template.getStatus() != ENABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板已停用");
        }
        String title = renderer.render(template.getTitleTemplate(), request.getParams());
        String content = renderer.render(template.getContentTemplate(), request.getParams());
        String contentType = StringUtils.hasText(template.getContentType())
                ? template.getContentType() : CONTENT_TYPE_TEXT;
        return systemMessageService.sendWithType(
                SecurityUtils.tryGetUserId(),
                template.getMessageType(),
                title,
                content,
                contentType,
                request.getBizType(),
                request.getBizId(),
                request.getReceiverIds());
    }

    private SysMessageTemplateDO selectByCode(String templateCode) {
        return templateMapper.selectOne(new LambdaQueryWrapper<SysMessageTemplateDO>()
                .eq(SysMessageTemplateDO::getTemplateCode, templateCode));
    }

    private void checkCodeUnique(String templateCode, Long excludeId) {
        if (!StringUtils.hasText(templateCode)) {
            return;
        }
        SysMessageTemplateDO exists = selectByCode(templateCode.trim());
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "模板编码已存在");
        }
    }

    private SysMessageTemplateDO toEntity(MessageTemplateSaveRequest request) {
        SysMessageTemplateDO template = new SysMessageTemplateDO();
        template.setId(request.getId());
        template.setTemplateCode(request.getTemplateCode().trim());
        template.setTemplateName(request.getTemplateName());
        template.setMessageType(StringUtils.hasText(request.getMessageType()) ? request.getMessageType() : "SYSTEM");
        template.setTitleTemplate(request.getTitleTemplate());
        template.setContentTemplate(request.getContentTemplate());
        template.setContentType(StringUtils.hasText(request.getContentType()) ? request.getContentType() : CONTENT_TYPE_TEXT);
        template.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        template.setRemark(request.getRemark());
        return template;
    }

    private MessageTemplateVo toVo(SysMessageTemplateDO template) {
        return MessageTemplateVo.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .templateName(template.getTemplateName())
                .messageType(template.getMessageType())
                .titleTemplate(template.getTitleTemplate())
                .contentTemplate(template.getContentTemplate())
                .contentType(template.getContentType())
                .status(template.getStatus())
                .remark(template.getRemark())
                .createdAt(template.getCreatedAt())
                .build();
    }
}
