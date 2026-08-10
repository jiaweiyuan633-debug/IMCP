package com.example.admin.module.report;

import com.example.admin.common.Result;
import com.example.admin.module.report.vo.ReportCenterVo;
import com.example.admin.module.report.vo.ReportScreenVo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 报表中心聚合数据 */
    @GetMapping("/center")
    @PreAuthorize("hasAuthority('report:center:view')")
    public Result<ReportCenterVo> center() {
        return Result.success(reportService.center());
    }

    /** 数据大屏聚合数据 */
    @GetMapping("/screen")
    @PreAuthorize("hasAuthority('report:screen:view')")
    public Result<ReportScreenVo> screen() {
        return Result.success(reportService.screen());
    }
}
