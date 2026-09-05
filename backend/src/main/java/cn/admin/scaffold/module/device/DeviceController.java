package cn.admin.scaffold.module.device;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.Idempotent;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.device.dto.DeviceQuery;
import cn.admin.scaffold.module.device.dto.DeviceSaveRequest;
import cn.admin.scaffold.module.device.dto.DeviceStatusRequest;
import cn.admin.scaffold.module.device.vo.DeviceVo;
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
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    @PreAuthorize("hasAuthority('device:device:list')")
    public Result<PageResult<DeviceVo>> page(DeviceQuery query) {
        return Result.success(deviceService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('device:device:add')")
    @OperLog(module = "设备管理", action = "新增设备")
    @Idempotent(key = "#request.deviceCode", expireSeconds = 30)
    public Result<Long> create(@Valid @RequestBody DeviceSaveRequest request) {
        return Result.success(deviceService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('device:device:edit')")
    @OperLog(module = "设备管理", action = "编辑设备")
    public Result<Void> update(@Valid @RequestBody DeviceSaveRequest request) {
        deviceService.update(request);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('device:device:status')")
    @OperLog(module = "设备管理", action = "修改设备状态")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody DeviceStatusRequest request) {
        deviceService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:device:delete')")
    @OperLog(module = "设备管理", action = "删除设备")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.success();
    }
}
