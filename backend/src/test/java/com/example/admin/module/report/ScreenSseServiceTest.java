package com.example.admin.module.report;

import com.example.admin.module.report.vo.ReportScreenVo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenSseServiceTest {

    private final ReportService reportService = mock(ReportService.class);

    /** 未 stub 的 JdbcTemplate 返回 null → resolveTenant 恒为默认租户 1，全部连接归同一租户便于断言。 */
    private ScreenSseService service() {
        when(reportService.screen()).thenReturn(ReportScreenVo.builder().build());
        return new ScreenSseService(reportService, mock(JdbcTemplate.class));
    }

    @Test
    void connectEvictsOldestConnectionWhenPerTenantLimitExceeded() {
        ScreenSseService service = service();
        service.setConnectionLimit(2);
        service.connect(1L);
        service.connect(1L);
        service.connect(1L);

        // 超限 → 回收最旧连接，仅保留最近 2 条
        assertThat(service.connectionCount(1L)).isEqualTo(2);

        // 存活连接仍参与本轮快照推送（列表非空，pushNow 不会提前 return）
        service.pushNow(1L);
        verify(reportService, times(1)).screen();
    }

    @Test
    void connectionLimitZeroMeansUnlimited() {
        ScreenSseService service = service();
        service.setConnectionLimit(0);
        service.connect(1L);
        service.connect(1L);
        service.connect(1L);

        // 上限 0 = 不限制，3 条全部保留
        assertThat(service.connectionCount(1L)).isEqualTo(3);

        service.pushNow(1L);
        verify(reportService, times(1)).screen();
    }
}
