package cn.admin.scaffold.module.monitor;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.module.monitor.vo.ServerMonitorVo;
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

