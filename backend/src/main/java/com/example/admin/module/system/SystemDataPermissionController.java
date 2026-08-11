package com.example.admin.module.system;

import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.DataPermissionSaveRequest;
import com.example.admin.module.system.entity.SysDataPermissionDO;
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

import java.util.List;

/**
 * 数据权限表-列映射配置（批次2b）。维护 sys_data_permission，CRUD 后缓存即时重载。
 */
@RestController
@RequestMapping("/api/system/data-permission")
@RequiredArgsConstructor
public class SystemDataPermissionController {

    private final SystemDataPermissionService dataPermissionService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:data-permission:list')")
    public Result<List<SysDataPermissionDO>> list() {
        return Result.success(dataPermissionService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:data-permission:add')")
    @OperLog(module = "数据权限", action = "新增数据权限配置")
    public Result<Long> create(@Valid @RequestBody DataPermissionSaveRequest request) {
        return Result.success(dataPermissionService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:data-permission:edit')")
    @OperLog(module = "数据权限", action = "编辑数据权限配置")
    public Result<Void> update(@Valid @RequestBody DataPermissionSaveRequest request) {
        dataPermissionService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:data-permission:delete')")
    @OperLog(module = "数据权限", action = "删除数据权限配置")
    public Result<Void> delete(@PathVariable Long id) {
        dataPermissionService.delete(id);
        return Result.success();
    }

    @PostMapping("/reload")
    @PreAuthorize("hasAuthority('system:data-permission:edit')")
    @OperLog(module = "数据权限", action = "重载数据权限配置")
    public Result<Void> reload() {
        dataPermissionService.reload();
        return Result.success();
    }
}
