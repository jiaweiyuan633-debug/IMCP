package cn.admin.scaffold.module.system.warmflow;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.dto.WorkflowDelegateRequest;
import cn.admin.scaffold.module.system.entity.SysWorkflowDO;
import cn.admin.scaffold.module.system.entity.SysWorkflowLogDO;
import jakarta.validation.Valid;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "工作流", description = "流程实例/审批/驳回/撤回/转办")
@RestController
@RequestMapping("/api/system/workflow-engine")
@RequiredArgsConstructor
public class WarmFlowWorkflowController {

    private final WarmFlowWorkflowService workflowService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<PageResult<SysWorkflowDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) Long defId) {
        return Result.success(workflowService.page(pageNum, pageSize, status, processName, bizType, applicantId, defId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<WorkflowDetailVo> detail(@PathVariable Long id) {
        return Result.success(workflowService.detail(id));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<PageResult<SysWorkflowDO>> tasks(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String processName) {
        return Result.success(workflowService.taskPage(pageNum, pageSize, processName));
    }

    @PostMapping
    @OperLog(module = "工作流", action = "发起流程")
    public Result<Long> create(@RequestBody SysWorkflowDO workflow) {
        return Result.success(workflowService.create(workflow));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('system:workflow:approve')")
    @OperLog(module = "工作流", action = "审批通过")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody WarmFlowWorkflowActionRequest request) {
        workflowService.approve(id, request.getTaskId(), request.getNodeId(), request.getRemark());
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('system:workflow:reject')")
    @OperLog(module = "工作流", action = "审批拒绝")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody WarmFlowWorkflowActionRequest request) {
        workflowService.reject(id, request.getTaskId(), request.getNodeId(), request.getRemark());
        return Result.success();
    }

    @PutMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    @OperLog(module = "工作流", action = "撤回流程")
    public Result<Void> withdraw(@PathVariable Long id, @RequestBody(required = false) WarmFlowWorkflowActionRequest request) {
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
    public Result<List<SysWorkflowLogDO>> logs(@PathVariable Long id) {
        return Result.success(workflowService.logs(id));
    }

    @GetMapping("/{id}/nodes")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<List<WarmFlowProcessNodeVO>> currentNodes(@PathVariable Long id) {
        return Result.success(workflowService.currentNodes(id));
    }
}
