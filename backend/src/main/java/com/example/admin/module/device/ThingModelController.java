package com.example.admin.module.device;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.Idempotent;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.device.dto.ThingModelQuery;
import com.example.admin.module.device.dto.ThingModelSaveRequest;
import com.example.admin.module.device.vo.ThingModelSchemaVo;
import com.example.admin.module.device.vo.ThingModelVo;
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
@RequestMapping("/api/device/thing-model")
@RequiredArgsConstructor
public class ThingModelController {

    private final ThingModelService thingModelService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('device:thing-model:list')")
    public Result<PageResult<ThingModelVo>> page(ThingModelQuery query) {
        return Result.success(thingModelService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device:thing-model:view')")
    public Result<ThingModelVo> detail(@PathVariable Long id) {
        return Result.success(thingModelService.detail(id));
    }

    @GetMapping("/{id}/schema")
    @PreAuthorize("hasAuthority('device:thing-model:view')")
    public Result<ThingModelSchemaVo> schema(@PathVariable Long id) {
        return Result.success(thingModelService.schema(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('device:thing-model:add')")
    @OperLog(module = "设备物模型", action = "新增物模型")
    @Idempotent(key = "#request.deviceType", expireSeconds = 30)
    public Result<Long> create(@Valid @RequestBody ThingModelSaveRequest request) {
        return Result.success(thingModelService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('device:thing-model:edit')")
    @OperLog(module = "设备物模型", action = "编辑物模型")
    public Result<Void> update(@Valid @RequestBody ThingModelSaveRequest request) {
        thingModelService.update(request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:thing-model:delete')")
    @OperLog(module = "设备物模型", action = "删除物模型")
    public Result<Void> delete(@PathVariable Long id) {
        thingModelService.delete(id);
        return Result.success();
    }
}
