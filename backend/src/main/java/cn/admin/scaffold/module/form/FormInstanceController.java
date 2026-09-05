package cn.admin.scaffold.module.form;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.Idempotent;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.form.dto.FormInstanceQuery;
import cn.admin.scaffold.module.form.dto.FormInstanceSubmitRequest;
import cn.admin.scaffold.module.form.vo.FormInstanceVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 表单提交接口：提交、分页、详情、审批流转。
 */
@RestController
@RequestMapping("/api/form/instance")
@RequiredArgsConstructor
public class FormInstanceController {

    private final FormInstanceService formInstanceService;

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('form:instance:submit')")
    @OperLog(module = "表单引擎", action = "提交表单")
    @Idempotent(key = "#request.bizNo", expireSeconds = 10)
    public Result<Long> submit(@Valid @RequestBody FormInstanceSubmitRequest request) {
        return Result.success(formInstanceService.submit(request));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('form:instance:list')")
    public Result<PageResult<FormInstanceVo>> page(FormInstanceQuery query) {
        return Result.success(formInstanceService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('form:instance:view')")
    public Result<FormInstanceVo> getById(@PathVariable Long id) {
        return Result.success(formInstanceService.getById(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('form:instance:approve')")
    @OperLog(module = "表单引擎", action = "审批提交")
    public Result<Void> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        formInstanceService.approve(id, body.get("status"));
        return Result.success();
    }
}
