package cn.admin.scaffold.module.monitor.job;

import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.monitor.entity.SysJobDO;
import cn.admin.scaffold.module.monitor.entity.SysJobLogDO;
import cn.admin.scaffold.module.monitor.mapper.SysJobLogMapper;
import cn.admin.scaffold.module.monitor.mapper.SysJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：Quartz 执行线程无租户上下文，任务必须以自身 tenant_id 就位租户上下文。
 *
 * <p>背景：sys_job 在租户白名单内，旧代码在 {@link SysQuartzJob#execute} 里先
 * {@code jobMapper.selectById(jobId)}——Quartz 触发线程 TenantContext 为空，拦截器注入默认
 * tenant_id=1，非租户 1 的任务取回 null、租户上下文留在默认 1，任务在错租户下执行且执行日志
 * 写错租户。修复后改走 {@code selectByIdIgnoreTenant} 跨租户取回任务，再以任务自有租户就位。
 * 本测试断言执行日志必须归属任务自有租户而非默认租户 1。
 */
class SysQuartzJobTest {

    private SysJobMapper jobMapper;
    private SysJobLogMapper jobLogMapper;
    private BusinessMetrics businessMetrics;
    private SysQuartzJob job;
    private JobExecutionContext context;

    @BeforeEach
    void setUp() {
        jobMapper = mock(SysJobMapper.class);
        jobLogMapper = mock(SysJobLogMapper.class);
        businessMetrics = mock(BusinessMetrics.class);
        job = new SysQuartzJob();
        ReflectionTestUtils.setField(job, "jobMapper", jobMapper);
        ReflectionTestUtils.setField(job, "jobLogMapper", jobLogMapper);
        ReflectionTestUtils.setField(job, "businessMetrics", businessMetrics);
        context = mock(JobExecutionContext.class);
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("jobId", 42L);
        dataMap.put("jobName", "同步数据");
        dataMap.put("jobGroup", "DEFAULT");
        // 非法 invokeTarget -> IllegalArgumentException 走失败分支，不依赖 Spring 容器
        dataMap.put("invokeTarget", "a");
        when(context.getMergedJobDataMap()).thenReturn(dataMap);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executeRunsJobUnderOwnerTenantAndCleansContext() {
        // 租户 2 的任务：若回退到 selectById（拦截器注入 tenant_id=1 挡回），上下文保持默认 1、
        // 日志 tenant_id=1，此断言即失败
        SysJobDO owner = new SysJobDO();
        owner.setId(42L);
        owner.setTenantId(2L);
        owner.setJobName("同步数据");
        owner.setJobGroup("DEFAULT");
        owner.setInvokeTarget("a");
        when(jobMapper.selectByIdIgnoreTenant(42L)).thenReturn(owner);

        job.execute(context);

        ArgumentCaptor<SysJobLogDO> captor = ArgumentCaptor.forClass(SysJobLogDO.class);
        verify(jobLogMapper).insert(captor.capture());
        SysJobLogDO log = captor.getValue();
        assertThat(log.getTenantId())
                .as("执行日志必须归属任务自有租户，而非默认租户 1")
                .isEqualTo(2L);
        assertThat(log.getStatus()).isEqualTo(0);
        verify(businessMetrics).jobExecution(false);
        assertThat(TenantContext.getTenantId())
                .as("执行结束必须清理租户上下文，避免污染调度线程")
                .isEqualTo(1L);
    }

    @Test
    void executeFallsBackToDefaultTenantWhenJobMissing() {
        // 任务已被删除：selectByIdIgnoreTenant 返回 null，上下文保持默认（1），
        // 但日志仍按 dataMap 元数据落一条失败记录，且不抛异常
        when(jobMapper.selectByIdIgnoreTenant(42L)).thenReturn(null);

        job.execute(context);

        ArgumentCaptor<SysJobLogDO> captor = ArgumentCaptor.forClass(SysJobLogDO.class);
        verify(jobLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }
}
