package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.monitor.dto.AlertRuleSaveRequest;
import com.example.admin.module.monitor.entity.SysAlertRule;
import com.example.admin.module.monitor.mapper.SysAlertRuleMapper;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final SysAlertRuleMapper ruleMapper;

    public List<SysAlertRule> listAll() {
        return ruleMapper.selectList(new LambdaQueryWrapper<SysAlertRule>()
                .orderByAsc(SysAlertRule::getId));
    }

    public PageResult<SysAlertRule> page(long pageNum, long pageSize, String ruleName, Integer enabled) {
        Page<SysAlertRule> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAlertRule> wrapper = new LambdaQueryWrapper<SysAlertRule>()
                .like(StringUtils.hasText(ruleName), SysAlertRule::getRuleName, ruleName)
                .eq(enabled != null, SysAlertRule::getEnabled, enabled)
                .orderByAsc(SysAlertRule::getId);
        IPage<SysAlertRule> result = ruleMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public Long create(AlertRuleSaveRequest request) {
        SysAlertRule rule = toEntity(request);
        rule.setCreatedBy(tryGetUserId());
        ruleMapper.insert(rule);
        return rule.getId();
    }

    public void update(AlertRuleSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "规则 ID 不能为空");
        }
        SysAlertRule rule = toEntity(request);
        rule.setUpdatedBy(tryGetUserId());
        ruleMapper.updateById(rule);
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    private SysAlertRule toEntity(AlertRuleSaveRequest request) {
        SysAlertRule rule = new SysAlertRule();
        rule.setId(request.getId());
        rule.setRuleName(request.getRuleName());
        rule.setMetric(request.getMetric());
        rule.setOperator(request.getOperator());
        rule.setThreshold(request.getThreshold());
        rule.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        rule.setSeverity(request.getSeverity() == null ? "WARNING" : request.getSeverity());
        rule.setSilenceMinutes(request.getSilenceMinutes() == null ? 10 : request.getSilenceMinutes());
        rule.setWebhookUrl(request.getWebhookUrl());
        rule.setRemark(request.getRemark());
        return rule;
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (BusinessException exception) {
            return null;
        }
    }
}
