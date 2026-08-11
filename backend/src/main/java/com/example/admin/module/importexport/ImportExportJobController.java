package com.example.admin.module.importexport;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.Idempotent;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.importexport.dto.JobCreateRequest;
import com.example.admin.module.importexport.dto.JobQuery;
import com.example.admin.module.importexport.vo.DownloadVo;
import com.example.admin.module.importexport.vo.JobVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导入导出任务接口：创建导入/导出任务、任务分页/详情、导出结果下载。
 */
@RestController
@RequestMapping("/api/import-export/job")
@RequiredArgsConstructor
public class ImportExportJobController {

    private final ImportExportJobService jobService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('importexport:job:list')")
    public Result<PageResult<JobVo>> page(JobQuery query) {
        return Result.success(jobService.page(query));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('importexport:job:create')")
    @OperLog(module = "导入导出", action = "创建导入任务")
    @Idempotent(key = "#request.bizNo", expireSeconds = 30)
    public Result<Long> createImport(@Valid @RequestBody JobCreateRequest request) {
        return Result.success(jobService.createImport(request));
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('importexport:job:create')")
    @OperLog(module = "导入导出", action = "创建导出任务")
    @Idempotent(key = "#request.bizNo", expireSeconds = 30)
    public Result<Long> createExport(@Valid @RequestBody JobCreateRequest request) {
        return Result.success(jobService.createExport(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('importexport:job:view')")
    public Result<JobVo> view(@PathVariable Long id) {
        return Result.success(jobService.view(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('importexport:job:download')")
    public Result<DownloadVo> download(@PathVariable Long id) {
        return Result.success(jobService.download(id));
    }
}
