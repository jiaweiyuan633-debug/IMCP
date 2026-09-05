package {{package}};

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import {{package}}.dto.{{Entity}}Query;
import {{package}}.dto.{{Entity}}SaveRequest;
import {{package}}.vo.{{Entity}}Vo;
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

/**
 * {{comment}}接口：分页（GET 基路径）、详情、新增、编辑、删除。
 */
@RestController
@RequestMapping("/api/{{module}}/{{kebab}}")
@RequiredArgsConstructor
public class {{Entity}}Controller {

    private final {{Entity}}Service {{entity}}Service;

    @GetMapping
    @PreAuthorize("hasAuthority('{{permPrefix}}:list')")
    public Result<PageResult<{{Entity}}Vo>> page({{Entity}}Query query) {
        return Result.success({{entity}}Service.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('{{permPrefix}}:view')")
    public Result<{{Entity}}Vo> getById(@PathVariable Long id) {
        return Result.success({{entity}}Service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('{{permPrefix}}:add')")
    @OperLog(module = "{{comment}}", action = "新增{{comment}}")
    public Result<Long> create(@Valid @RequestBody {{Entity}}SaveRequest request) {
        return Result.success({{entity}}Service.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('{{permPrefix}}:edit')")
    @OperLog(module = "{{comment}}", action = "编辑{{comment}}")
    public Result<Void> update(@Valid @RequestBody {{Entity}}SaveRequest request) {
        {{entity}}Service.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('{{permPrefix}}:delete')")
    @OperLog(module = "{{comment}}", action = "删除{{comment}}")
    public Result<Void> delete(@PathVariable Long id) {
        {{entity}}Service.delete(id);
        return Result.success();
    }
}
