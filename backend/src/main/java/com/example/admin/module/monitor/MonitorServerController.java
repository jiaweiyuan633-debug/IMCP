package com.example.admin.module.monitor;

import com.example.admin.common.Result;
import com.example.admin.module.monitor.vo.ServerMonitorVo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor/server")
@RequiredArgsConstructor
public class MonitorServerController {

    private final ServerMonitorService serverMonitorService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:server:list')")
    public Result<ServerMonitorVo> get() {
        return Result.success(serverMonitorService.get());
    }
}

