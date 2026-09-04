package cn.admin.scaffold.module.form;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.Idempotent;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.form.dto.FormDefinitionQuery;
import cn.admin.scaffold.module.form.dto.FormDefinitionSaveRequest;
import cn.admin.scaffold.module.form.vo.FormDefinitionVo;
import cn.admin.scaffold.module.form.vo.FormSchemaVo;
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
 * 表单定义接口：分页、详情、渲染结构、新增、编辑、发布、删除。
 */
@RestController
@RequestMapping("/api/form/definition")
@RequiredArgsConstructor
public class FormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('form:definition:list')")
    public Result<PageResult<FormDefinitionVo>> page(FormDefinitionQuery query) {
        return Result.success(formDefinitionService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('form:definition:view')")
    public Result<FormDefinitionVo> getById(@PathVariable Long id) {
        return Result.success(formDefinitionService.getById(id));
    }

    @GetMapping("/{id}/schema")
    @PreAuthorize("hasAuthority('form:definition:view')")
    public Result<FormSchemaVo> getSchema(@PathVariable Long id) {
        return Result.success(formDefinitionService.getSchema(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('form:definition:add')")
    @OperLog(module = "表单引擎", action = "新增表单")
    @Idempotent(key = "#request.code", expireSeconds = 30)
    public Result<Long> create(@Valid @RequestBody FormDefinitionSaveRequest request) {
        return Result.success(formDefinitionService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('form:definition:edit')")
    @OperLog(module = "表单引擎", action = "编辑表单")
    public Result<Void> update(@Valid @RequestBody FormDefinitionSaveRequest request) {
        formDefinitionService.update(request);
        return Result.success();
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('form:definition:publish')")
    @OperLog(module = "表单引擎", action = "发布表单")
    public Result<Void> publish(@PathVariable Long id) {
        formDefinitionService.publish(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('form:definition:delete')")
    @OperLog(module = "表单引擎", action = "删除表单")
    public Result<Void> delete(@PathVariable Long id) {
        formDefinitionService.delete(id);
        return Result.success();
    }
}
