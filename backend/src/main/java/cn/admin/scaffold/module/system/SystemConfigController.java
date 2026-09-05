package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.dto.ConfigQuery;
import cn.admin.scaffold.module.system.dto.ConfigSaveRequest;
import cn.admin.scaffold.module.system.vo.ConfigVo;
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
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:config:list')")
    public Result<PageResult<ConfigVo>> page(ConfigQuery query) {
        return Result.success(configService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:config:add')")
    @OperLog(module = "参数配置", action = "新增参数")
    public Result<Long> create(@Valid @RequestBody ConfigSaveRequest request) {
        return Result.success(configService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:config:edit')")
    @OperLog(module = "参数配置", action = "编辑参数")
    public Result<Void> update(@Valid @RequestBody ConfigSaveRequest request) {
        configService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:delete')")
    @OperLog(module = "参数配置", action = "删除参数")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.success();
    }
}

