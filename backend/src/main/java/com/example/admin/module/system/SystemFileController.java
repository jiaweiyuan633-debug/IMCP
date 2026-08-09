package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.entity.SysFileDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/file")
@RequiredArgsConstructor
public class SystemFileController {

    private final SystemFileService fileService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:file:list')")
    public Result<PageResult<SysFileDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String originalName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String storageType) {
        return Result.success(fileService.page(pageNum, pageSize, fileName, originalName, category, storageType));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:file:delete')")
    @OperLog(module = "文件管理", action = "删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }
}
