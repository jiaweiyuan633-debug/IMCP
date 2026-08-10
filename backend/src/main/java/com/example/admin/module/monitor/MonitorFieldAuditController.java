package com.example.admin.module.monitor;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.module.system.entity.SysFieldAuditLogDO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor/field-audit")
@RequiredArgsConstructor
public class MonitorFieldAuditController {

    private final MonitorFieldAuditService fieldAuditService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:audit:list')")
    public Result<PageResult<SysFieldAuditLogDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action) {
        return Result.success(fieldAuditService.page(pageNum, pageSize, module, entityName, action));
    }
}
