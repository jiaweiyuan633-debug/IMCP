package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.entity.SysTenantDO;
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
@RequestMapping("/api/system/tenant")
@RequiredArgsConstructor
public class SystemTenantController {

    private final SystemTenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public Result<PageResult<SysTenantDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String tenantName) {
        return Result.success(tenantService.page(pageNum, pageSize, tenantName));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:add')")
    @OperLog(module = "租户管理", action = "新增租户")
    public Result<Long> create(@RequestBody SysTenantDO tenant) {
        return Result.success(tenantService.create(tenant));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:tenant:edit')")
    @OperLog(module = "租户管理", action = "编辑租户")
    public Result<Void> update(@RequestBody SysTenantDO tenant) {
        tenantService.update(tenant);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:delete')")
    @OperLog(module = "租户管理", action = "删除租户")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.success();
    }
}

