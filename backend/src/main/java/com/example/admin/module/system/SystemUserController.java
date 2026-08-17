package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.UserQuery;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.vo.UserVo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SystemUserController {

    private final SystemUserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageResult<UserVo>> page(UserQuery query) {
        return Result.success(userService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @OperLog(module = "用户管理", action = "新增用户")
    public Result<Long> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.success(userService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:user:edit')")
    @OperLog(module = "用户管理", action = "编辑用户")
    public Result<Void> update(@Valid @RequestBody UserSaveRequest request) {
        userService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperLog(module = "用户管理", action = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:status')")
    @OperLog(module = "用户管理", action = "修改用户状态")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:role')")
    @OperLog(module = "用户管理", action = "分配角色")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody RoleIdsRequest request) {
        userService.assignRoles(id, request.getRoleIds());
        return Result.success();
    }

    @PutMapping("/{id}/posts")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @OperLog(module = "用户管理", action = "分配岗位")
    public Result<Void> assignPosts(@PathVariable Long id, @Valid @RequestBody PostIdsRequest request) {
        userService.assignPosts(id, request.getPostIds());
        return Result.success();
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:user:list')")
    public void export(HttpServletResponse response) throws IOException {
        userService.exportUsers(response);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('system:user:add')")
    @OperLog(module = "用户管理", action = "导入用户")
    public Result<SystemUserService.ImportResult> importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(userService.importUsers(file));
    }
}

