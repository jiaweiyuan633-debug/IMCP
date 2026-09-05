package cn.admin.scaffold.module.monitor.job;

import cn.admin.scaffold.module.monitor.entity.SysJobDO;
import cn.admin.scaffold.module.monitor.mapper.SysJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：启动重新调度必须跨租户拉取全部启用任务。
 *
 * <p>背景：旧代码在 {@link SysJobSchedulerService#initJobs} 里 {@code selectList(status=1)}——
 * @PostConstruct 阶段无租户上下文，拦截器注入默认 tenant_id=1、只拉回租户 1 的任务，
 * 其他租户任务重启后触发器不被重建。修复后改走 {@code selectEnabledIgnoreTenant} 跨租户扫描。
 * 本测试用跨租户任务集断言调度器为每个任务都注册了触发器。
 */
class SysJobSchedulerServiceTest {

    private Scheduler scheduler;
    private SysJobMapper jobMapper;
    private SysJobSchedulerService service;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = mock(Scheduler.class);
        jobMapper = mock(SysJobMapper.class);
        service = new SysJobSchedulerService(scheduler, jobMapper);
        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);
    }

    @Test
    void initJobsSchedulesJobsFromAllTenants() throws Exception {
        SysJobDO tenant1 = job(1L, 1L);
        SysJobDO tenant2 = job(2L, 2L);
        when(jobMapper.selectEnabledIgnoreTenant()).thenReturn(List.of(tenant1, tenant2));

        service.initJobs();

        // 若回退到 selectList（拦截器注入 tenant_id=1），跨租户任务不被注册，此断言即失败
        verify(jobMapper).selectEnabledIgnoreTenant();
        verify(scheduler, times(2)).scheduleJob(any(Trigger.class));
    }

    @Test
    void initJobsSkipsSchedulingWhenNoEnabledJob() throws Exception {
        when(jobMapper.selectEnabledIgnoreTenant()).thenReturn(List.of());

        service.initJobs();

        verify(scheduler, times(0)).scheduleJob(any(Trigger.class));
        // 空扫描不触发异常
        assertThat(service).isNotNull();
    }

    private SysJobDO job(Long id, Long tenantId) {
        SysJobDO job = new SysJobDO();
        job.setId(id);
        job.setTenantId(tenantId);
        job.setJobName("任务" + id);
        job.setJobGroup("DEFAULT");
        job.setInvokeTarget("a.b");
        job.setCronExpression("0 0 12 * * ?");
        return job;
    }
}
