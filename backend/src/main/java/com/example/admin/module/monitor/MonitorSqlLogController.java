package com.example.admin.module.monitor;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.module.monitor.vo.SqlLogVo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor/sql-log")
@RequiredArgsConstructor
public class MonitorSqlLogController {

    private final MonitorSqlLogService sqlLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:sql:list')")
    public Result<PageResult<SqlLogVo>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String sqlText) {
        return Result.success(sqlLogService.page(pageNum, pageSize, sqlText));
    }
}

