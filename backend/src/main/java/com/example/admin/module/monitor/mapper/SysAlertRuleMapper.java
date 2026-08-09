package com.example.admin.module.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.example.admin.module.monitor.entity.SysAlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysAlertRuleMapper extends BaseMapper<SysAlertRule> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_alert_rule WHERE enabled = 1")
    List<SysAlertRule> selectAllEnabledIgnoreTenant();
}
