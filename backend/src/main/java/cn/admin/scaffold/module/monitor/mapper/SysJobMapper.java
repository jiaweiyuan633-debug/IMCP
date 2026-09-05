package cn.admin.scaffold.module.monitor.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.admin.scaffold.module.monitor.entity.SysJobDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysJobMapper extends BaseMapper<SysJobDO> {

    /**
     * 按主键跨租户查询任务（Quartz 执行线程用）：绕过 TenantLine 拦截器。
     * <p>sys_job 在租户白名单内，Quartz 触发线程 TenantContext 为空时拦截器注入默认
     * tenant_id=1，非租户 1 的任务 selectById 返回 null、执行器跳过租户就位、任务落到
     * 租户 1 上下文执行并写错租户日志。执行器须先以任务自身 tenant_id 就位租户上下文
     * （与 SysUserMapper.selectByIdIgnoreTenant 同模式）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_job WHERE id = #{jobId}")
    SysJobDO selectByIdIgnoreTenant(@Param("jobId") Long jobId);

    /**
     * 跨租户扫描全部启用任务（启动重新调度用）：绕过 TenantLine 拦截器。
     * <p>@PostConstruct 阶段无租户上下文，拦截器注入默认 tenant_id=1 会漏掉其他租户的
     * 任务、重启后其触发器不被重建。系统级调度扫描须跨租户（与 AiTaskMapper.selectTenantIds 同模式）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_job WHERE status = 1 ORDER BY id")
    List<SysJobDO> selectEnabledIgnoreTenant();
}

