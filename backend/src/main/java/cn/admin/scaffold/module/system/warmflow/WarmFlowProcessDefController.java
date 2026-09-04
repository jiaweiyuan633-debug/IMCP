package cn.admin.scaffold.module.system.warmflow;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.dto.ProcessDefSaveRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/system/workflow-engine/def")
@RequiredArgsConstructor
public class WarmFlowProcessDefController {

    private final WarmFlowProcessDefService processDefService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<PageResult<WarmFlowProcessDefVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String defName,
            @RequestParam(required = false) Integer status) {
        return Result.success(processDefService.page(pageNum, pageSize, defName, status));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<List<WarmFlowProcessDefVO>> options() {
        return Result.success(processDefService.listOptions());
    }

    @GetMapping("/{id}/nodes")
    @PreAuthorize("hasAuthority('system:workflow:list')")
    public Result<List<WarmFlowProcessNodeVO>> nodes(@PathVariable Long id) {
        return Result.success(processDefService.nodes(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:workflow:def:add')")
    @OperLog(module = "工作流", action = "新增流程定义")
    public Result<Long> create(@Valid @RequestBody ProcessDefSaveRequest request) {
        return Result.success(processDefService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:workflow:def:edit')")
    @OperLog(module = "工作流", action = "编辑流程定义")
    public Result<Void> update(@Valid @RequestBody ProcessDefSaveRequest request) {
        processDefService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:workflow:def:delete')")
    @OperLog(module = "工作流", action = "删除流程定义")
    public Result<Void> delete(@PathVariable Long id) {
        processDefService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('system:workflow:def:edit')")
    @OperLog(module = "工作流", action = "发布流程定义")
    public Result<Void> publish(@PathVariable Long id) {
        processDefService.publish(id);
        return Result.success();
    }

    @PutMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('system:workflow:def:edit')")
    @OperLog(module = "工作流", action = "取消发布流程定义")
    public Result<Void> unpublish(@PathVariable Long id) {
        processDefService.unPublish(id);
        return Result.success();
    }
}
