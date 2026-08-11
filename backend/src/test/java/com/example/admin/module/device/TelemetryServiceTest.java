package com.example.admin.module.device;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.device.dto.TelemetryPoint;
import com.example.admin.module.device.dto.TelemetryReportRequest;
import com.example.admin.module.device.entity.DeviceDO;
import com.example.admin.module.device.entity.TelemetryDO;
import com.example.admin.module.device.mapper.DeviceMapper;
import com.example.admin.module.device.mapper.TelemetryMapper;
import com.example.admin.module.device.vo.TelemetryLatestVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 遥测服务单元测试：value 数值/文本分流、points 空校验、deviceId 存在性校验、批量逐条插入。
 */
class TelemetryServiceTest {

    private TelemetryMapper telemetryMapper;
    private DeviceMapper deviceMapper;
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryMapper = mock(TelemetryMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        telemetryService = new TelemetryService(telemetryMapper, deviceMapper);
    }

    @Test
    void emptyPointsRejected() {
        TelemetryReportRequest request = new TelemetryReportRequest();
        request.setTelemetryId("T1");
        request.setDeviceId(1L);
        request.setPoints(List.of());

        assertThatThrownBy(() -> telemetryService.report(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("遥测数据点不能为空");
    }

    @Test
    void deviceNotFoundRejected() {
        when(deviceMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> telemetryService.report(request(999L, point("temperature", 36.5, t10()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DATA_NOT_FOUND.getMessage());
    }

    @Test
    void numericValueMapsToValueNum() {
        when(deviceMapper.selectById(1L)).thenReturn(new DeviceDO());

        telemetryService.report(request(1L, point("temperature", 36.5, t10())));

        ArgumentCaptor<TelemetryDO> captor = ArgumentCaptor.forClass(TelemetryDO.class);
        verify(telemetryMapper).insert(captor.capture());
        TelemetryDO row = captor.getValue();
        assertThat(row.getPropertyKey()).isEqualTo("temperature");
        assertThat(row.getValueNum()).isEqualByComparingTo(new BigDecimal("36.5"));
        assertThat(row.getValueText()).isNull();
    }

    @Test
    void textValueMapsToValueText() {
        when(deviceMapper.selectById(1L)).thenReturn(new DeviceDO());

        telemetryService.report(request(1L, point("status", "online", t10())));

        ArgumentCaptor<TelemetryDO> captor = ArgumentCaptor.forClass(TelemetryDO.class);
        verify(telemetryMapper).insert(captor.capture());
        TelemetryDO row = captor.getValue();
        assertThat(row.getValueNum()).isNull();
        assertThat(row.getValueText()).isEqualTo("online");
    }

    @Test
    void multiplePointsInsertedSequentially() {
        when(deviceMapper.selectById(1L)).thenReturn(new DeviceDO());

        telemetryService.report(request(1L,
                point("temperature", 36.5, t10()),
                point("status", "online", t10().plusMinutes(1))));

        verify(telemetryMapper, times(2)).insert(any(TelemetryDO.class));
    }

    @Test
    void latestRejectsNullDeviceId() {
        assertThatThrownBy(() -> telemetryService.latest(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("设备 ID 不能为空");
    }

    @Test
    void latestTakesLatestOccurredAtPerProperty() {
        TelemetryDO older = row(1L, "temperature", new BigDecimal("36.5"), t10());
        TelemetryDO newer = row(2L, "temperature", new BigDecimal("37.0"), t10().plusMinutes(1));
        TelemetryDO status = row(3L, "status", "online", t10().plusMinutes(1));
        // 模拟数据库按 occurred_at desc, id desc 返回
        when(telemetryMapper.selectList(any())).thenReturn(List.of(newer, status, older));

        List<TelemetryLatestVo> result = telemetryService.latest(1L);

        assertThat(result).hasSize(2);
        assertThat(valueOf(result, "temperature")).isEqualTo("37.0");
        assertThat(valueOf(result, "status")).isEqualTo("online");
    }

    @Test
    void latestUsesIdTiebreakerWhenOccurredAtEqual() {
        // 同属性、同 occurred_at 两条：id 更大的保留，避免把旧值当最新值（与 history() 的 id 次级排序一致）
        TelemetryDO oldInsert = row(1L, "temperature", new BigDecimal("36.5"), t10());
        TelemetryDO newInsert = row(2L, "temperature", new BigDecimal("37.0"), t10());
        when(telemetryMapper.selectList(any())).thenReturn(List.of(newInsert, oldInsert));

        List<TelemetryLatestVo> result = telemetryService.latest(1L);

        assertThat(result).hasSize(1);
        assertThat(valueOf(result, "temperature")).isEqualTo("37.0");
    }

    private TelemetryLatestVo latestOf(List<TelemetryLatestVo> result, String key) {
        return result.stream()
                .filter(v -> key.equals(v.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private String valueOf(List<TelemetryLatestVo> result, String key) {
        return String.valueOf(latestOf(result, key).getValue());
    }

    private TelemetryDO row(Long id, String key, Object value, LocalDateTime occurredAt) {
        TelemetryDO row = new TelemetryDO();
        row.setId(id);
        row.setPropertyKey(key);
        if (value instanceof Number number) {
            row.setValueNum(new BigDecimal(number.toString()));
        } else {
            row.setValueText(value == null ? null : value.toString());
        }
        row.setOccurredAt(occurredAt);
        return row;
    }

    private TelemetryReportRequest request(Long deviceId, TelemetryPoint... points) {
        TelemetryReportRequest request = new TelemetryReportRequest();
        request.setTelemetryId("T-" + System.nanoTime());
        request.setDeviceId(deviceId);
        request.setPoints(List.of(points));
        return request;
    }

    private TelemetryPoint point(String key, Object value, LocalDateTime occurredAt) {
        TelemetryPoint point = new TelemetryPoint();
        point.setKey(key);
        point.setValue(value);
        point.setOccurredAt(occurredAt);
        return point;
    }

    private LocalDateTime t10() {
        return LocalDateTime.of(2026, 8, 10, 10, 0);
    }
}
