package com.example.admin.module.report;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.Idempotent;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.report.dto.ReportDefinitionQuery;
import com.example.admin.module.report.dto.ReportDefinitionSaveRequest;
import com.example.admin.module.report.dto.ReportExecuteRequest;
import com.example.admin.module.report.vo.ReportDefinitionVo;
import com.example.admin.module.report.vo.ReportExecuteResultVo;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表定义管理接口：定义 CRUD + 只读查询执行。
 */
@RestController
@RequestMapping("/api/report/definition")
@RequiredArgsConstructor
public class ReportDefinitionController {

    private final ReportDefinitionService reportDefinitionService;

    /** 报表定义分页（name/code/category 模糊） */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('report:definition:list')")
    public Result<PageResult<ReportDefinitionVo>> page(ReportDefinitionQuery query) {
        return Result.success(reportDefinitionService.page(query));
    }

    /** 新增报表定义（code 租户内唯一） */
    @PostMapping
    @PreAuthorize("hasAuthority('report:definition:add')")
    @OperLog(module = "报表定义", action = "新增报表")
    @Idempotent(key = "#request.code", expireSeconds = 30)
    public Result<Long> create(@Valid @RequestBody ReportDefinitionSaveRequest request) {
        return Result.success(reportDefinitionService.create(request));
    }

    /** 编辑报表定义 */
    @PutMapping
    @PreAuthorize("hasAuthority('report:definition:edit')")
    @OperLog(module = "报表定义", action = "编辑报表")
    public Result<Void> update(@Valid @RequestBody ReportDefinitionSaveRequest request) {
        reportDefinitionService.update(request);
        return Result.success();
    }

    /** 逻辑删除报表定义 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('report:definition:delete')")
    @OperLog(module = "报表定义", action = "删除报表")
    public Result<Void> delete(@PathVariable Long id) {
        reportDefinitionService.delete(id);
        return Result.success();
    }

    /** 执行报表只读查询，返回 {columns, rows} */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('report:definition:execute')")
    public Result<ReportExecuteResultVo> execute(@PathVariable Long id, @RequestBody ReportExecuteRequest request) {
        return Result.success(reportDefinitionService.execute(id, request));
    }

    /** 报表定义详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('report:definition:view')")
    public Result<ReportDefinitionVo> detail(@PathVariable Long id) {
        return Result.success(reportDefinitionService.detail(id));
    }
}
