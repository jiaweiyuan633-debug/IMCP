package com.example.admin.module.monitor;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.monitor.dto.JobQuery;
import com.example.admin.module.monitor.dto.JobSaveRequest;
import com.example.admin.module.monitor.entity.SysJobDO;
import com.example.admin.module.monitor.vo.JobLogVo;
import com.example.admin.module.monitor.vo.SchedulerStatusVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor/job")
@RequiredArgsConstructor
public class MonitorJobController {

    private final MonitorJobService jobService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:job:list')")
    public Result<PageResult<SysJobDO>> page(JobQuery query) {
        return Result.success(jobService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('monitor:job:add')")
    @OperLog(module = "定时任务", action = "新增任务")
    public Result<Long> create(@Valid @RequestBody JobSaveRequest request) {
        return Result.success(jobService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('monitor:job:edit')")
    @OperLog(module = "定时任务", action = "编辑任务")
    public Result<Void> update(@Valid @RequestBody JobSaveRequest request) {
        jobService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('monitor:job:delete')")
    @OperLog(module = "定时任务", action = "删除任务")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('monitor:job:changeStatus')")
    @OperLog(module = "定时任务", action = "修改任务状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        jobService.changeStatus(id, request.getStatus());
        return Result.success();
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('monitor:job:run')")
    @OperLog(module = "定时任务", action = "立即执行")
    public Result<Void> runOnce(@PathVariable Long id) {
        jobService.runOnce(id);
        return Result.success();
    }

    @GetMapping("/scheduler/status")
    @PreAuthorize("hasAuthority('monitor:job:list')")
    public Result<SchedulerStatusVo> schedulerStatus() {
        return Result.success(jobService.schedulerStatus());
    }

    @GetMapping("/log")
    @PreAuthorize("hasAuthority('monitor:job:list')")
    public Result<PageResult<JobLogVo>> logPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String jobName) {
        return Result.success(jobService.logPage(pageNum, pageSize, jobName));
    }
}

