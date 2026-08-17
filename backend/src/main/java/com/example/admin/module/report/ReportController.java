package com.example.admin.module.report;

import com.example.admin.common.BusinessException;
import com.example.admin.common.Result;
import com.example.admin.common.ResultCode;
import com.example.admin.common.SseTicketService;
import com.example.admin.module.report.vo.ReportCenterVo;
import com.example.admin.module.report.vo.ReportScreenVo;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "报表", description = "报表执行与定义")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ScreenSseService screenSseService;
    private final SseTicketService sseTicketService;

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

    /** 数据大屏 SSE 订阅票据：登录态签发，60s 内有效（EventSource 无法携带 Header，走一次性票据）。 */
    @GetMapping("/screen/ticket")
    @PreAuthorize("hasAuthority('report:screen:view')")
    public Result<String> screenTicket() {
        return Result.success(sseTicketService.issue(SecurityUtils.getUserId()));
    }

    /** 数据大屏实时推送流：每 30s 推一次该租户聚合快照（事件名 screen）。 */
    @GetMapping(value = "/screen/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter screenStream(@RequestParam String ticket) {
        Long userId = sseTicketService.consume(ticket);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return screenSseService.connect(userId);
    }
}
