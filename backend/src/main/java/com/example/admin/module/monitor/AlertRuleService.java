package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.SsrfUrlValidator;
import com.example.admin.module.monitor.dto.AlertRuleSaveRequest;
import com.example.admin.module.monitor.entity.SysAlertRuleDO;
import com.example.admin.module.monitor.mapper.SysAlertRuleMapper;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private static final int ENABLED = 1;
    private static final String DEFAULT_SEVERITY = "WARNING";
    private static final int DEFAULT_SILENCE_MINUTES = 10;

    private final SysAlertRuleMapper ruleMapper;

    public PageResult<SysAlertRuleDO> page(long pageNum, long pageSize, String ruleName, Integer enabled) {
        Page<SysAlertRuleDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAlertRuleDO> wrapper = new LambdaQueryWrapper<SysAlertRuleDO>()
                .like(StringUtils.hasText(ruleName), SysAlertRuleDO::getRuleName, ruleName)
                .eq(enabled != null, SysAlertRuleDO::getEnabled, enabled)
                .orderByAsc(SysAlertRuleDO::getId);
        IPage<SysAlertRuleDO> result = ruleMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public Long create(AlertRuleSaveRequest request) {
        checkWebhookUrl(request.getWebhookUrl());
        SysAlertRuleDO rule = toEntity(request);
        rule.setCreatedBy(SecurityUtils.tryGetUserId());
        ruleMapper.insert(rule);
        return rule.getId();
    }

    public void update(AlertRuleSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "规则 ID 不能为空");
        }
        checkWebhookUrl(request.getWebhookUrl());
        SysAlertRuleDO rule = toEntity(request);
        rule.setUpdatedBy(SecurityUtils.tryGetUserId());
        ruleMapper.updateById(rule);
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    private SysAlertRuleDO toEntity(AlertRuleSaveRequest request) {
        SysAlertRuleDO rule = new SysAlertRuleDO();
        rule.setId(request.getId());
        rule.setRuleName(request.getRuleName());
        rule.setMetric(request.getMetric());
        rule.setOperator(request.getOperator());
        rule.setThreshold(request.getThreshold());
        rule.setEnabled(request.getEnabled() == null ? ENABLED : request.getEnabled());
        rule.setSeverity(request.getSeverity() == null ? DEFAULT_SEVERITY : request.getSeverity());
        rule.setSilenceMinutes(request.getSilenceMinutes() == null
                ? DEFAULT_SILENCE_MINUTES
                : request.getSilenceMinutes());
        rule.setWebhookUrl(request.getWebhookUrl());
        rule.setRemark(request.getRemark());
        return rule;
    }

    /**
     * 保存时静态校验 Webhook 地址（协议/主机/IP 字面量，不发起 DNS，避免保存依赖解析）。
     * R4-1.13：告警触发时服务端会主动请求该地址，若不限制可被用作 SSRF 跳板打内网。
     */
    private void checkWebhookUrl(String webhookUrl) {
        String error = SsrfUrlValidator.validateOutboundHttpUrl(webhookUrl);
        if (error != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "Webhook 地址不合法：" + error);
        }
    }

}
