package cn.admin.scaffold.module.monitor.job;

import cn.admin.scaffold.module.monitor.entity.SysJobDO;
import cn.admin.scaffold.module.monitor.mapper.SysJobMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobSchedulerService {

    private final Scheduler scheduler;
    private final SysJobMapper jobMapper;

    @PostConstruct
    public void initJobs() {
        // R4-1.18：@PostConstruct 阶段无租户上下文，selectList 会被拦截器注入默认
        // tenant_id=1、只拉回租户 1 的启用任务，其他租户任务重启后触发器不被重建。
        // 启动扫描必须跨租户拉取全部启用任务。
        List<SysJobDO> jobs = jobMapper.selectEnabledIgnoreTenant();
        for (SysJobDO job : jobs) {
            scheduleJob(job);
        }
        log.info("Loaded {} enabled scheduled jobs", jobs.size());
    }

    public void scheduleJob(SysJobDO job) {
        JobKey jobKey = jobKey(job);
        TriggerKey triggerKey = triggerKey(job);
        try {
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("jobId", job.getId());
            dataMap.put("jobName", job.getJobName());
            dataMap.put("jobGroup", job.getJobGroup());
            dataMap.put("invokeTarget", job.getInvokeTarget());

            JobDetail jobDetail = JobBuilder.newJob(SysQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(dataMap)
                    .build();
            // replace=true 的原子 upsert：多副本同时启动/编辑时不会抛 AlreadyExistsException，
            // 由 Quartz JDBC 存储保证最终一致，避免启动竞态
            scheduler.addJob(jobDetail, true);

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(cronSchedule(job))
                    .build();
            if (scheduler.checkExists(triggerKey)) {
                // 触发器已存在则原子替换（更新 cron / misfire 策略）
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException | RuntimeException exception) {
            log.error("Failed to schedule job {}", job.getId(), exception);
        }
    }

    private CronScheduleBuilder cronSchedule(SysJobDO job) {
        CronScheduleBuilder cronSchedule = CronScheduleBuilder.cronSchedule(job.getCronExpression());
        if ("2".equals(job.getMisfirePolicy())) {
            cronSchedule.withMisfireHandlingInstructionFireAndProceed();
        } else if ("3".equals(job.getMisfirePolicy())) {
            cronSchedule.withMisfireHandlingInstructionIgnoreMisfires();
        } else {
            cronSchedule.withMisfireHandlingInstructionDoNothing();
        }
        return cronSchedule;
    }

    public void deleteJob(SysJobDO job) {
        try {
            scheduler.deleteJob(jobKey(job));
        } catch (SchedulerException | RuntimeException exception) {
            log.error("Failed to delete job {}", job.getId(), exception);
        }
    }

    public void runOnce(SysJobDO job) {
        try {
            JobKey key = jobKey(job);
            if (!scheduler.checkExists(key)) {
                scheduleJob(job);
            }
            scheduler.triggerJob(key);
        } catch (SchedulerException | RuntimeException exception) {
            log.error("Failed to run job {}", job.getId(), exception);
        }
    }

    private JobKey jobKey(SysJobDO job) {
        return JobKey.jobKey("job-" + job.getId(), job.getJobGroup());
    }

    private TriggerKey triggerKey(SysJobDO job) {
        return TriggerKey.triggerKey(jobKey(job).getName() + "-trigger", job.getJobGroup());
    }
}

