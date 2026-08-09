package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.WorkflowDelegateRequest;
import jakarta.validation.Valid;
import com.example.admin.module.system.entity.SysWorkflow;
import com.example.admin.module.system.entity.SysWorkflowLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/workflow")
@RequiredArgsConstructor
public class SystemWorkflowController {

    private final SystemWorkflowService workflowService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<PageResult<SysWorkflow>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(workflowService.page(pageNum, pageSize, status));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<PageResult<SysWorkflow>> tasks(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(workflowService.taskPage(pageNum, pageSize));
    }

    @PostMapping
    @OperLog(module = "工作流", action = "发起流程")
    public Result<Long> create(@RequestBody SysWorkflow workflow) {
        return Result.success(workflowService.create(workflow));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('system:workflow:approve')")
    @OperLog(module = "工作流", action = "审批通过")
    public Result<Void> approve(@PathVariable Long id, @RequestBody WorkflowRemarkRequest request) {
        workflowService.approve(id, request.getRemark());
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('system:workflow:reject')")
    @OperLog(module = "工作流", action = "审批拒绝")
    public Result<Void> reject(@PathVariable Long id, @RequestBody WorkflowRemarkRequest request) {
        workflowService.reject(id, request.getRemark());
        return Result.success();
    }

    @PutMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    @OperLog(module = "工作流", action = "撤回流程")
    public Result<Void> withdraw(@PathVariable Long id, @RequestBody(required = false) WorkflowRemarkRequest request) {
        workflowService.withdraw(id, request == null ? null : request.getRemark());
        return Result.success();
    }

    @PutMapping("/{id}/delegate")
    @PreAuthorize("hasAuthority('system:workflow:approve')")
    @OperLog(module = "工作流", action = "转办流程")
    public Result<Void> delegate(@PathVariable Long id, @Valid @RequestBody WorkflowDelegateRequest request) {
        workflowService.delegate(id, request.getDelegateUserId());
        return Result.success();
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<List<SysWorkflowLog>> logs(@PathVariable Long id) {
        return Result.success(workflowService.logs(id));
    }
}

