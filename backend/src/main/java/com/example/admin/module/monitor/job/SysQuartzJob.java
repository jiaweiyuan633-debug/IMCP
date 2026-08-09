package com.example.admin.module.monitor.job;

import com.example.admin.module.monitor.entity.SysJobLog;
import com.example.admin.module.monitor.mapper.SysJobLogMapper;
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

    @Autowired
    private SysJobLogMapper jobLogMapper;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long jobId = dataMap.getLong("jobId");
        String jobName = dataMap.getString("jobName");
        String jobGroup = dataMap.getString("jobGroup");
        String invokeTarget = dataMap.getString("invokeTarget");

        LocalDateTime start = LocalDateTime.now();
        String message;
        String error = null;
        boolean success;
        try {
            JobInvokeUtil.invoke(invokeTarget);
            message = "执行成功";
            success = true;
        } catch (Exception exception) {
            success = false;
            message = "执行失败";
            error = exception.toString();
            log.error("Job {} execute failed", jobId, exception);
        }

        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobName(jobName);
        jobLog.setJobGroup(jobGroup);
        jobLog.setInvokeTarget(invokeTarget);
        jobLog.setJobMessage(message);
        jobLog.setStatus(success ? 1 : 0);
        jobLog.setExceptionInfo(error);
        jobLog.setStartTime(start);
        jobLog.setEndTime(LocalDateTime.now());
        jobLogMapper.insert(jobLog);
    }
}

