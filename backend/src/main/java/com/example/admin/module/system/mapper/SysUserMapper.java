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

    /**
     * 登录专用查询（R1-1.7）：按用户名（+ 可选租户）跨租户定位用户，绕过 TenantLine 拦截器。
     * <p>登录时租户上下文尚未就位，拦截器注入默认 tenant_id=1 会使非租户 1 用户无法登录；
     * 又因用户名按 (tenant_id, username) 唯一（V33），未指定租户时跨租户可能同名多行。
     * tenantId 非 null 时精确限定租户（配合登录表单可选租户字段，规避多行）；
     * 为 null 时查询全部租户，由调用方对多行结果做业务处理。
     * 自定义 SQL 不受 @TableLogic 自动拼接，需手动携带 deleted = 0。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 "
            + "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY id")
    List<SysUserDO> selectByUsername(@Param("username") String username, @Param("tenantId") Long tenantId);
}

