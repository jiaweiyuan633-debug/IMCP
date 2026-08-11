package com.example.admin.module.device;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.Idempotent;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.device.dto.TelemetryQuery;
import com.example.admin.module.device.dto.TelemetryReportRequest;
import com.example.admin.module.device.vo.TelemetryLatestVo;
import com.example.admin.module.device.vo.TelemetryPointVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/device/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/report")
    @PreAuthorize("hasAuthority('device:telemetry:report')")
    @OperLog(module = "设备遥测", action = "上报遥测")
    @Idempotent(key = "#request.telemetryId", expireSeconds = 5)
    public Result<Void> report(@Valid @RequestBody TelemetryReportRequest request) {
        telemetryService.report(request);
        return Result.success();
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('device:telemetry:list')")
    public Result<List<TelemetryLatestVo>> latest(@RequestParam Long deviceId) {
        return Result.success(telemetryService.latest(deviceId));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('device:telemetry:list')")
    public Result<PageResult<TelemetryPointVo>> history(TelemetryQuery query) {
        return Result.success(telemetryService.history(query));
    }
}
