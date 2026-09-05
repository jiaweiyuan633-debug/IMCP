package cn.admin.scaffold.module.monitor;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.monitor.vo.DashboardStatsVo;
import cn.admin.scaffold.module.monitor.vo.OnlineUserVo;
import cn.admin.scaffold.module.system.entity.SysLoginLogDO;
import cn.admin.scaffold.module.system.entity.SysOperLogDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "监控", description = "服务器/SQL/登录日志/操作日志/缓存/在线用户")
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

