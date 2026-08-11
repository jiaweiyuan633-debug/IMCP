package com.example.admin.module.importexport;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.Idempotent;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.importexport.dto.TemplateQuery;
import com.example.admin.module.importexport.dto.TemplateSaveRequest;
import com.example.admin.module.importexport.vo.TemplateVo;
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
 * 导入导出模板接口。
 */
@RestController
@RequestMapping("/api/import-export/template")
@RequiredArgsConstructor
public class ImportExportTemplateController {

    private final ImportExportTemplateService templateService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('importexport:template:list')")
    public Result<PageResult<TemplateVo>> page(TemplateQuery query) {
        return Result.success(templateService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('importexport:template:add')")
    @OperLog(module = "导入导出", action = "新增模板")
    @Idempotent(key = "#request.code", expireSeconds = 30)
    public Result<Long> create(@Valid @RequestBody TemplateSaveRequest request) {
        return Result.success(templateService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('importexport:template:edit')")
    @OperLog(module = "导入导出", action = "编辑模板")
    public Result<Void> update(@Valid @RequestBody TemplateSaveRequest request) {
        templateService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('importexport:template:delete')")
    @OperLog(module = "导入导出", action = "删除模板")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }
}
