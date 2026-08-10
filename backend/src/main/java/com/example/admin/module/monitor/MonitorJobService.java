package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.monitor.dto.JobQuery;
import com.example.admin.module.monitor.dto.JobSaveRequest;
import com.example.admin.module.monitor.entity.SysJobDO;
import com.example.admin.module.monitor.entity.SysJobLogDO;
import com.example.admin.module.monitor.job.SysJobSchedulerService;
import com.example.admin.module.monitor.mapper.SysJobLogMapper;
import com.example.admin.module.monitor.mapper.SysJobMapper;
import com.example.admin.module.monitor.vo.JobLogVo;
import com.example.admin.module.monitor.vo.SchedulerStatusVo;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorJobService {

    private static final int ENABLED = 1;
    private static final int DEFAULT_STATUS = 0;
    private static final int DEFAULT_CONCURRENT = 1;
    private static final String DEFAULT_MISFIRE_POLICY = "1";

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final SysJobSchedulerService schedulerService;
    private final Scheduler scheduler;
    private final JdbcTemplate jdbcTemplate;

    public PageResult<SysJobDO> page(JobQuery query) {
        Page<SysJobDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysJobDO> wrapper = new LambdaQueryWrapper<SysJobDO>()
                .like(StringUtils.hasText(query.getJobName()), SysJobDO::getJobName, query.getJobName())
                .like(StringUtils.hasText(query.getJobGroup()), SysJobDO::getJobGroup, query.getJobGroup())
                .eq(query.getStatus() != null, SysJobDO::getStatus, query.getStatus())
                .orderByDesc(SysJobDO::getId);
        IPage<SysJobDO> result = jobMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public Long create(JobSaveRequest request) {
        SysJobDO job = toEntity(request);
        job.setCreatedBy(SecurityUtils.tryGetUserId());
        jobMapper.insert(job);
        if (job.getStatus() != null && job.getStatus() == ENABLED) {
            schedulerService.scheduleJob(job);
        }
        return job.getId();
    }

    public void update(JobSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "任务 ID 不能为空");
        }
        SysJobDO job = toEntity(request);
        jobMapper.updateById(job);
        if (job.getStatus() != null && job.getStatus() == ENABLED) {
            schedulerService.scheduleJob(job);
        } else {
            schedulerService.deleteJob(job);
        }
    }

    @Transactional
    public void delete(Long id) {
        SysJobDO job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        schedulerService.deleteJob(job);
        jobMapper.deleteById(id);
    }

    public void changeStatus(Long id, Integer status) {
        SysJobDO job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        job.setStatus(status);
        jobMapper.updateById(job);
        if (status == ENABLED) {
            schedulerService.scheduleJob(job);
        } else {
            schedulerService.deleteJob(job);
        }
    }

    public void runOnce(Long id) {
        SysJobDO job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        schedulerService.runOnce(job);
    }

    /**
     * 调度集群状态：Quartz 元数据 + QRTZ 调度表实时统计。
     * 多副本部署时 nodeCount > 1 表示各实例共享同一 QRTZ 存储，触发器由集群互斥消费。
     */
    public SchedulerStatusVo schedulerStatus() {
        try {
            SchedulerMetaData meta = scheduler.getMetaData();
            return SchedulerStatusVo.builder()
                    .clustered(meta.isJobStoreClustered())
                    .instanceId(scheduler.getSchedulerInstanceId())
                    .instanceName(scheduler.getSchedulerName())
                    .threadPoolSize(meta.getThreadPoolSize())
                    .nodeCount(countRows("SELECT COUNT(*) FROM QRTZ_SCHEDULER_STATE"))
                    .jobCount(countRows("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS"))
                    .triggerCount(countRows("SELECT COUNT(*) FROM QRTZ_TRIGGERS"))
                    .pausedTriggerCount(countRows("SELECT COUNT(*) FROM QRTZ_TRIGGERS WHERE TRIGGER_STATE = 'PAUSED'"))
                    .errorTriggerCount(countRows("SELECT COUNT(*) FROM QRTZ_TRIGGERS WHERE TRIGGER_STATE = 'ERROR'"))
                    .firedTriggerCount(countRows("SELECT COUNT(*) FROM QRTZ_FIRED_TRIGGERS"))
                    .overdueTriggerCount(countRows("SELECT COUNT(*) FROM QRTZ_TRIGGERS WHERE TRIGGER_STATE IN ('WAITING','ACQUIRED') AND NEXT_FIRE_TIME IS NOT NULL AND NEXT_FIRE_TIME < " + System.currentTimeMillis()))
                    .build();
        } catch (SchedulerException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "查询调度器状态失败");
        }
    }

    private int countRows(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    public PageResult<JobLogVo> logPage(long pageNum, long pageSize, String jobName) {
        Page<SysJobLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysJobLogDO> wrapper = new LambdaQueryWrapper<SysJobLogDO>()
                .like(StringUtils.hasText(jobName), SysJobLogDO::getJobName, jobName)
                .orderByDesc(SysJobLogDO::getId);
        IPage<SysJobLogDO> result = jobLogMapper.selectPage(page, wrapper);
        List<JobLogVo> records = result.getRecords().stream()
                .map(this::toJobLogVo)
                .toList();
        return PageResult.of(result, records);
    }

    private JobLogVo toJobLogVo(SysJobLogDO log) {
        return JobLogVo.builder()
                .id(log.getId())
                .jobName(log.getJobName())
                .jobGroup(log.getJobGroup())
                .invokeTarget(log.getInvokeTarget())
                .jobMessage(log.getJobMessage())
                .status(log.getStatus())
                .exceptionInfo(log.getExceptionInfo())
                .startTime(log.getStartTime())
                .endTime(log.getEndTime())
                .build();
    }

    private SysJobDO toEntity(JobSaveRequest request) {
        SysJobDO job = new SysJobDO();
        job.setId(request.getId());
        job.setJobName(request.getJobName());
        job.setJobGroup(request.getJobGroup());
        job.setInvokeTarget(request.getInvokeTarget());
        job.setCronExpression(request.getCronExpression());
        job.setMisfirePolicy(request.getMisfirePolicy() == null
                ? DEFAULT_MISFIRE_POLICY
                : request.getMisfirePolicy());
        job.setConcurrent(request.getConcurrent() == null ? DEFAULT_CONCURRENT : request.getConcurrent());
        job.setStatus(request.getStatus() == null ? DEFAULT_STATUS : request.getStatus());
        job.setRemark(request.getRemark());
        return job;
    }

}

