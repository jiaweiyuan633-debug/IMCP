package com.example.admin.module.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.example.admin.module.monitor.entity.SysAlertRuleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysAlertRuleMapper extends BaseMapper<SysAlertRuleDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, rule_name, metric, operator, threshold, enabled,
                   severity, silence_minutes, webhook_url, remark,
                   created_by, created_at, updated_at, updated_by, version
            FROM sys_alert_rule WHERE enabled = 1
            """)
    List<SysAlertRuleDO> selectAllEnabledIgnoreTenant();
}
