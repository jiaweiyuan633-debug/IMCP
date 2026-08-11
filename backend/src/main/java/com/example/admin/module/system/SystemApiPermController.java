package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.ApiPermQuery;
import com.example.admin.module.system.dto.ApiPermSaveRequest;
import com.example.admin.module.system.vo.ApiPermVo;
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

@RestController
@RequestMapping("/api/system/api-perm")
@RequiredArgsConstructor
public class SystemApiPermController {

    private final SystemApiPermService apiPermService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:api-perm:list')")
    public Result<PageResult<ApiPermVo>> page(ApiPermQuery query) {
        return Result.success(apiPermService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:api-perm:add')")
    @OperLog(module = "接口权限", action = "新增权限映射")
    public Result<Long> create(@Valid @RequestBody ApiPermSaveRequest request) {
        return Result.success(apiPermService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:api-perm:edit')")
    @OperLog(module = "接口权限", action = "编辑权限映射")
    public Result<Void> update(@Valid @RequestBody ApiPermSaveRequest request) {
        apiPermService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:api-perm:delete')")
    @OperLog(module = "接口权限", action = "删除权限映射")
    public Result<Void> delete(@PathVariable Long id) {
        apiPermService.delete(id);
        return Result.success();
    }

    @PostMapping("/reload")
    @PreAuthorize("hasAuthority('system:api-perm:reload')")
    @OperLog(module = "接口权限", action = "重载权限映射")
    public Result<Void> reload() {
        apiPermService.reload();
        return Result.success();
    }
}
