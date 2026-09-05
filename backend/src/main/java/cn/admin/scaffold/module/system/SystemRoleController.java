package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.dto.RoleQuery;
import cn.admin.scaffold.module.system.dto.RoleSaveRequest;
import cn.admin.scaffold.module.system.vo.RoleOptionVo;
import cn.admin.scaffold.module.system.vo.RoleVo;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "角色管理", description = "角色 CRUD/菜单授权/数据权限")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SystemRoleController {

    private final SystemRoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<RoleVo>> page(RoleQuery query) {
        return Result.success(roleService.page(query));
    }

    @GetMapping("/options")
    public Result<List<RoleOptionVo>> options() {
        return Result.success(roleService.options());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    @OperLog(module = "角色管理", action = "新增角色")
    public Result<Long> create(@Valid @RequestBody RoleSaveRequest request) {
        return Result.success(roleService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", action = "编辑角色")
    public Result<Void> update(@Valid @RequestBody RoleSaveRequest request) {
        roleService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperLog(module = "角色管理", action = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:assign')")
    @OperLog(module = "角色管理", action = "分配菜单权限")
    public Result<Void> assignMenus(@PathVariable Long id, @Valid @RequestBody MenuIdsRequest request) {
        roleService.assignMenus(id, request.getMenuIds());
        return Result.success();
    }
}

