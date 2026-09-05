package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.dto.MenuSaveRequest;
import cn.admin.scaffold.module.system.vo.MenuVo;
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

@Tag(name = "菜单管理", description = "菜单与按钮权限树")
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SystemMenuController {

    private final SystemMenuService menuService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<List<MenuVo>> tree() {
        return Result.success(menuService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:add')")
    @OperLog(module = "菜单管理", action = "新增菜单")
    public Result<Long> create(@Valid @RequestBody MenuSaveRequest request) {
        return Result.success(menuService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @OperLog(module = "菜单管理", action = "编辑菜单")
    public Result<Void> update(@Valid @RequestBody MenuSaveRequest request) {
        menuService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @OperLog(module = "菜单管理", action = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }
}

