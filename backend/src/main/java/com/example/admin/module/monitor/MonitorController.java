package com.example.admin.module.monitor;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.monitor.vo.DashboardStatsVo;
import com.example.admin.module.monitor.vo.OnlineUserVo;
import com.example.admin.module.system.entity.SysLoginLogDO;
import com.example.admin.module.system.entity.SysOperLogDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping("/login-log")
    @PreAuthorize("hasAuthority('monitor:login-log:list')")
    public Result<PageResult<SysLoginLogDO>> loginLogPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username) {
        return Result.success(monitorService.loginLogPage(pageNum, pageSize, username));
    }

    @GetMapping("/oper-log")
    @PreAuthorize("hasAuthority('monitor:oper-log:list')")
    public Result<PageResult<SysOperLogDO>> operLogPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String module) {
        return Result.success(monitorService.operLogPage(pageNum, pageSize, module));
    }

    @GetMapping("/online")
    @PreAuthorize("hasAuthority('monitor:online:list')")
    public Result<List<OnlineUserVo>> onlineUsers() {
        return Result.success(monitorService.onlineUsers());
    }

    @DeleteMapping("/online/{tokenId}")
    @PreAuthorize("hasAuthority('monitor:online:kick')")
    @OperLog(module = "在线用户", action = "强制下线")
    public Result<Void> kick(@PathVariable String tokenId) {
        monitorService.kick(tokenId);
        return Result.success();
    }

    @DeleteMapping("/cache/{key}")
    @PreAuthorize("hasAuthority('monitor:cache:delete')")
    @OperLog(module = "缓存管理", action = "清理缓存")
    public Result<Void> clearCache(@PathVariable String key) {
        monitorService.clearCache(key);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<DashboardStatsVo> stats() {
        return Result.success(monitorService.stats());
    }
}

