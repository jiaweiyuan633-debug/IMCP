package com.example.admin.module.monitor;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.module.system.entity.SysAuditLogDO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/monitor/audit-log")
@RequiredArgsConstructor
public class MonitorAuditController {

    private final MonitorAuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:audit:list')")
    public Result<PageResult<SysAuditLogDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer status) {
        return Result.success(auditService.page(pageNum, pageSize, module, status));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('monitor:audit:list')")
    public void export(HttpServletResponse response) throws IOException {
        auditService.export(response);
    }
}
