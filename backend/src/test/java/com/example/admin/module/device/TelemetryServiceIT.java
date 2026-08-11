package com.example.admin.module.device;

import com.example.admin.AbstractIntegrationTest;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.device.dto.DeviceSaveRequest;
import com.example.admin.module.device.dto.TelemetryPoint;
import com.example.admin.module.device.dto.TelemetryQuery;
import com.example.admin.module.device.dto.TelemetryReportRequest;
import com.example.admin.module.device.vo.TelemetryLatestVo;
import com.example.admin.module.device.vo.TelemetryPointVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 遥测服务集成测试：上报 → latest/history 往返 + deviceId 校验 + 租户隔离在真实 MySQL 上生效。
 */
class TelemetryServiceIT extends AbstractIntegrationTest {

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reportThenLatestAndHistoryRoundTrip() {
        Long deviceId = createDevice("IT-TEL-001", "遥测设备");

        LocalDateTime t1 = LocalDateTime.of(2026, 8, 10, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 10, 10, 1, 0);
        report(deviceId, "T-1", List.of(
                point("temperature", 36.5, t1),
                point("temperature", 37.2, t2),
                point("status", "online", t2)));

        // 各属性最新值：temperature 取 t2 的 37.2（MySQL DECIMAL 读写会带 8 位小数，按数值比较）
        List<TelemetryLatestVo> latest = telemetryService.latest(deviceId);
        assertThat(latest).extracting(TelemetryLatestVo::getKey)
                .containsExactlyInAnyOrder("temperature", "status");
        TelemetryLatestVo tempLatest = latest.stream()
                .filter(v -> "temperature".equals(v.getKey()))
                .findFirst()
                .orElseThrow();
        assertThat(((BigDecimal) tempLatest.getValue()).doubleValue()).isEqualTo(37.2);

        // 时序分页：属性过滤 + occurred_at 范围 + 倒序（最新在前）
        TelemetryQuery query = new TelemetryQuery();
        query.setDeviceId(deviceId);
        query.setProperty("temperature");
        query.setStart(LocalDateTime.of(2026, 8, 10, 9, 0));
        query.setEnd(LocalDateTime.of(2026, 8, 10, 23, 0));
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<TelemetryPointVo> history = telemetryService.history(query);
        assertThat(history.getRecords()).hasSize(2);
        assertThat(history.getRecords().get(0).getOccurredAt()).isEqualTo(t2);
        assertThat(history.getRecords().get(1).getOccurredAt()).isEqualTo(t1);
    }

    @Test
    void reportRejectedWhenDeviceNotFound() {
        assertThatThrownBy(() -> report(999999L, "T-X",
                List.of(point("temperature", 36.5, LocalDateTime.of(2026, 8, 10, 10, 0)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void tenantIsolationKeepsTelemetryApart() {
        TenantContext.setTenantId(1L);
        Long device1 = createDevice("IT-TEL-100", "租户1设备");
        report(device1, "T-1", List.of(point("temperature", 36.5, LocalDateTime.of(2026, 8, 10, 10, 0))));

        TenantContext.setTenantId(2L);
        Long device2 = createDevice("IT-TEL-100", "租户2设备");
        report(device2, "T-2", List.of(point("temperature", 20.0, LocalDateTime.of(2026, 8, 10, 10, 0))));

        TenantContext.setTenantId(2L);
        assertThat(telemetryService.latest(device2))
                .extracting(TelemetryLatestVo::getValue)
                .extracting(v -> ((BigDecimal) v).doubleValue())
                .containsExactly(20.0);

        TenantContext.setTenantId(1L);
        assertThat(telemetryService.latest(device1))
                .extracting(TelemetryLatestVo::getValue)
                .extracting(v -> ((BigDecimal) v).doubleValue())
                .containsExactly(36.5);
    }

    private Long createDevice(String code, String name) {
        DeviceSaveRequest request = new DeviceSaveRequest();
        request.setDeviceCode(code);
        request.setDeviceName(name);
        request.setDeviceType("IT");
        request.setStatus(1);
        return deviceService.create(request);
    }

    private void report(Long deviceId, String telemetryId, List<TelemetryPoint> points) {
        TelemetryReportRequest request = new TelemetryReportRequest();
        request.setTelemetryId(telemetryId);
        request.setDeviceId(deviceId);
        request.setPoints(points);
        telemetryService.report(request);
    }

    private TelemetryPoint point(String key, Object value, LocalDateTime occurredAt) {
        TelemetryPoint point = new TelemetryPoint();
        point.setKey(key);
        point.setValue(value);
        point.setOccurredAt(occurredAt);
        return point;
    }
}
