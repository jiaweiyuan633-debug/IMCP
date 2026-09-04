package cn.admin.scaffold.module.report;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.report.dto.ScreenTemplateSaveRequest;
import cn.admin.scaffold.module.report.vo.ScreenTemplateVo;
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
 * 数据大屏模板接口：模板列表（内置 + 租户自定义）、详情、保存、编辑、删除。
 */
@RestController
@RequestMapping("/api/report/screen/template")
@RequiredArgsConstructor
public class ScreenTemplateController {

    private final ScreenTemplateService screenTemplateService;

    /** 模板列表：内置模板 + 当前租户自定义模板 */
    @GetMapping
    @PreAuthorize("hasAuthority('report:screen:template:list')")
    public Result<List<ScreenTemplateVo>> list() {
        return Result.success(screenTemplateService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('report:screen:template:list')")
    public Result<ScreenTemplateVo> detail(@PathVariable Long id) {
        return Result.success(screenTemplateService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('report:screen:template:add')")
    @OperLog(module = "数据大屏", action = "保存大屏模板")
    public Result<Long> create(@Valid @RequestBody ScreenTemplateSaveRequest request) {
        return Result.success(screenTemplateService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('report:screen:template:edit')")
    @OperLog(module = "数据大屏", action = "编辑大屏模板")
    public Result<Void> update(@Valid @RequestBody ScreenTemplateSaveRequest request) {
        screenTemplateService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('report:screen:template:delete')")
    @OperLog(module = "数据大屏", action = "删除大屏模板")
    public Result<Void> delete(@PathVariable Long id) {
        screenTemplateService.delete(id);
        return Result.success();
    }
}
