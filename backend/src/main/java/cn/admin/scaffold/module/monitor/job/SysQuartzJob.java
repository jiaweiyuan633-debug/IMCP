package cn.admin.scaffold.module.monitor.job;

import cn.admin.scaffold.module.monitor.entity.SysJobLogDO;
import cn.admin.scaffold.module.monitor.entity.SysJobDO;
import cn.admin.scaffold.module.monitor.mapper.SysJobLogMapper;
import cn.admin.scaffold.module.monitor.mapper.SysJobMapper;
import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@DisallowConcurrentExecution
public class SysQuartzJob implements Job {

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 0;

    @Autowired
    private SysJobLogMapper jobLogMapper;

    @Autowired
    private SysJobMapper jobMapper;

    @Autowired
    private BusinessMetrics businessMetrics;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long jobId = dataMap.getLong("jobId");
        String jobName = dataMap.getString("jobName");
        String jobGroup = dataMap.getString("jobGroup");
        String invokeTarget = dataMap.getString("invokeTarget");

        LocalDateTime start = LocalDateTime.now();
        String message = "执行成功";
        String error = null;
        boolean success = true;
        // Quartz 执行线程无租户上下文，selectById 会被拦截器注入默认 tenant_id=1，
        // 非租户 1 的任务取回 null、租户上下文留在默认 1、任务在错租户下执行并写错租户日志。
        // 必须跨租户取回任务，再以任务自有 tenant_id 就位租户上下文。
        SysJobDO job = jobMapper.selectByIdIgnoreTenant(jobId);
        if (job != null && job.getTenantId() != null) {
            TenantContext.setTenantId(job.getTenantId());
        }
        try {
            try {
                JobInvokeUtil.invoke(invokeTarget);
                message = "执行成功";
                success = true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                success = false;
                message = "执行失败";
                error = exception.toString();
                log.error("Job {} execute failed", jobId, exception);
            }
        } finally {
            SysJobLogDO jobLog = new SysJobLogDO();
            jobLog.setTenantId(TenantContext.getTenantId());
            jobLog.setJobName(jobName);
            jobLog.setJobGroup(jobGroup);
            jobLog.setInvokeTarget(invokeTarget);
            jobLog.setJobMessage(message);
            jobLog.setStatus(success ? STATUS_SUCCESS : STATUS_FAILURE);
            jobLog.setExceptionInfo(error);
            jobLog.setStartTime(start);
            jobLog.setEndTime(LocalDateTime.now());
            jobLogMapper.insert(jobLog);
            businessMetrics.jobExecution(success);
            TenantContext.clear();
        }
    }
}

