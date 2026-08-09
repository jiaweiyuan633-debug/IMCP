package com.example.admin.module.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.system.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {

    /**
     * 跨租户查询用户（租户管理员候选），绕过 TenantLine 拦截器。
     * tenantId 为 null 时返回全部租户的用户，否则仅返回指定租户的用户。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE #{tenantId} IS NULL OR tenant_id = #{tenantId} ORDER BY tenant_id, id")
    List<SysUserDO> selectAdminCandidates(@Param("tenantId") Long tenantId);
}

