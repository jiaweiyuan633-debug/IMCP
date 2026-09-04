package cn.admin.scaffold.module.monitor;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.monitor.dto.AlertRuleSaveRequest;
import cn.admin.scaffold.module.monitor.entity.SysAlertRuleDO;
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
@RequestMapping("/api/monitor/alert-rule")
@RequiredArgsConstructor
public class MonitorAlertController {

    private final AlertRuleService ruleService;
    private final AlertMonitorService alertMonitorService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:alert:list')")
    public Result<PageResult<SysAlertRuleDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) Integer enabled) {
        return Result.success(ruleService.page(pageNum, pageSize, ruleName, enabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('monitor:alert:add')")
    @OperLog(module = "监控告警", action = "新增规则")
    public Result<Long> create(@Valid @RequestBody AlertRuleSaveRequest request) {
        return Result.success(ruleService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('monitor:alert:edit')")
    @OperLog(module = "监控告警", action = "编辑规则")
    public Result<Void> update(@Valid @RequestBody AlertRuleSaveRequest request) {
        ruleService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('monitor:alert:delete')")
    @OperLog(module = "监控告警", action = "删除规则")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.success();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('monitor:alert:run')")
    @OperLog(module = "监控告警", action = "立即检查")
    public Result<Integer> runNow() {
        return Result.success(alertMonitorService.checkNow());
    }
}
