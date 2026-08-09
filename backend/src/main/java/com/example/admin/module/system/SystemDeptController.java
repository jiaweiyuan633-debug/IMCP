package com.example.admin.module.system;

import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.DeptSaveRequest;
import com.example.admin.module.system.vo.DeptVo;
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

@RestController
@RequestMapping("/api/system/dept")
@RequiredArgsConstructor
public class SystemDeptController {

    private final SystemDeptService deptService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public Result<List<DeptVo>> tree() {
        return Result.success(deptService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add')")
    @OperLog(module = "部门管理", action = "新增部门")
    public Result<Long> create(@Valid @RequestBody DeptSaveRequest request) {
        return Result.success(deptService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @OperLog(module = "部门管理", action = "编辑部门")
    public Result<Void> update(@Valid @RequestBody DeptSaveRequest request) {
        deptService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @OperLog(module = "部门管理", action = "删除部门")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success();
    }
}

