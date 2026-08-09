package com.example.admin.module.monitor.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.monitor.entity.SysJobDO;
import com.example.admin.module.monitor.mapper.SysJobMapper;
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
        List<SysJobDO> jobs = jobMapper.selectList(new LambdaQueryWrapper<SysJobDO>()
                .eq(SysJobDO::getStatus, 1));
        for (SysJobDO job : jobs) {
            scheduleJob(job);
        }
        log.info("Loaded {} enabled scheduled jobs", jobs.size());
    }

    public void scheduleJob(SysJobDO job) {
        JobKey jobKey = jobKey(job);
        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("jobId", job.getId());
            dataMap.put("jobName", job.getJobName());
            dataMap.put("jobGroup", job.getJobGroup());
            dataMap.put("invokeTarget", job.getInvokeTarget());

            JobDetail jobDetail = JobBuilder.newJob(SysQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(dataMap)
                    .build();
            CronScheduleBuilder cronSchedule = CronScheduleBuilder.cronSchedule(job.getCronExpression());
            if ("2".equals(job.getMisfirePolicy())) {
                cronSchedule.withMisfireHandlingInstructionFireAndProceed();
            } else if ("3".equals(job.getMisfirePolicy())) {
                cronSchedule.withMisfireHandlingInstructionIgnoreMisfires();
            } else {
                cronSchedule.withMisfireHandlingInstructionDoNothing();
            }
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobKey.getName() + "-trigger", job.getJobGroup())
                    .withSchedule(cronSchedule)
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException | RuntimeException exception) {
            log.error("Failed to schedule job {}", job.getId(), exception);
        }
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
}

