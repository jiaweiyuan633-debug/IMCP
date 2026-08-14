package com.example.admin.module.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.system.entity.SysWorkflowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysWorkflowMapper extends BaseMapper<SysWorkflowDO> {

    /**
     * 跨租户取全量工作流租户（定时扫描用），绕过 TenantLine 拦截器。
     * <p>{@code WorkflowTimeoutScanner} 的 @Scheduled 线程无租户上下文，若直接 selectList 会被
     * 拦截器注入默认 tenant_id=1 只扫到租户 1；先取全量租户逐个就位上下文再扫描（与
     * AiTaskScanner#selectTenantIds 同款模式）。sys_workflow 无逻辑删除列，无需 deleted 过滤。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM sys_workflow WHERE tenant_id IS NOT NULL ORDER BY tenant_id")
    List<Long> selectTenantIds();
}

