package cn.admin.scaffold.module.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.device.dto.TelemetryPoint;
import cn.admin.scaffold.module.device.dto.TelemetryQuery;
import cn.admin.scaffold.module.device.dto.TelemetryReportRequest;
import cn.admin.scaffold.module.device.entity.DeviceDO;
import cn.admin.scaffold.module.device.entity.TelemetryDO;
import cn.admin.scaffold.module.device.mapper.DeviceMapper;
import cn.admin.scaffold.module.device.mapper.TelemetryMapper;
import cn.admin.scaffold.module.device.vo.TelemetryLatestVo;
import cn.admin.scaffold.module.device.vo.TelemetryPointVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 遥测服务：批量上报、最新值、时序分页查询。
 * device_telemetry 为纯追加时序表（不设 version/deleted），按租户 + 设备归档。
 * 租户隔离由 TenantLineInnerInterceptor 自动注入，这里不手动操作 TenantContext。
 */
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryMapper telemetryMapper;
    private final DeviceMapper deviceMapper;

    /** latest() 单设备最多加载的行数：遥测为纯追加时序表，设备属性数有限，
     *  取最近若干行即可覆盖全部属性最新值，避免大设备全量加载 OOM。 */
    private static final int LATEST_MAX_ROWS = 500;

    @Transactional
    public void report(TelemetryReportRequest request) {
        if (request.getPoints() == null || request.getPoints().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "遥测数据点不能为空");
        }
        if (request.getDeviceId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "设备 ID 不能为空");
        }
        DeviceDO device = deviceMapper.selectById(request.getDeviceId());
        if (device == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        for (TelemetryPoint point : request.getPoints()) {
            telemetryMapper.insert(toEntity(request.getDeviceId(), point));
        }
    }

    public List<TelemetryLatestVo> latest(Long deviceId) {
        if (deviceId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "设备 ID 不能为空");
        }
        // 纯追加时序表：只取最近 LATEST_MAX_ROWS 行，覆盖全部属性最新值而非全量加载（防 OOM）；
        // id 次级排序保证同 occurred_at 并列时保留最新插入的那条，与 history() 决胜方式一致
        List<TelemetryDO> rows = telemetryMapper.selectList(new LambdaQueryWrapper<TelemetryDO>()
                .eq(TelemetryDO::getDeviceId, deviceId)
                .orderByDesc(TelemetryDO::getOccurredAt)
                .orderByDesc(TelemetryDO::getId)
                .last("LIMIT " + LATEST_MAX_ROWS));
        // 已按 occurred_at,id 倒序，每个属性保留首次出现的记录即最新值
        Map<String, TelemetryDO> latestByKey = new LinkedHashMap<>();
        for (TelemetryDO row : rows) {
            latestByKey.putIfAbsent(row.getPropertyKey(), row);
        }
        return latestByKey.values().stream().map(this::toLatestVo).toList();
    }

    public PageResult<TelemetryPointVo> history(TelemetryQuery query) {
        Page<TelemetryDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<TelemetryDO> wrapper = new LambdaQueryWrapper<TelemetryDO>()
                .eq(query.getDeviceId() != null, TelemetryDO::getDeviceId, query.getDeviceId())
                .eq(StringUtils.hasText(query.getProperty()), TelemetryDO::getPropertyKey, query.getProperty())
                .ge(query.getStart() != null, TelemetryDO::getOccurredAt, query.getStart())
                .le(query.getEnd() != null, TelemetryDO::getOccurredAt, query.getEnd())
                .orderByDesc(TelemetryDO::getOccurredAt)
                .orderByDesc(TelemetryDO::getId);
        IPage<TelemetryDO> result = telemetryMapper.selectPage(page, wrapper);
        List<TelemetryPointVo> records = result.getRecords().stream().map(this::toPointVo).toList();
        return PageResult.of(result, records);
    }

    private TelemetryDO toEntity(Long deviceId, TelemetryPoint point) {
        TelemetryDO row = new TelemetryDO();
        row.setDeviceId(deviceId);
        row.setPropertyKey(point.getKey());
        row.setOccurredAt(point.getOccurredAt());
        Object value = point.getValue();
        if (value instanceof Number number) {
            row.setValueNum(new BigDecimal(number.toString()));
        } else {
            row.setValueText(value == null ? null : value.toString());
        }
        return row;
    }

    private TelemetryLatestVo toLatestVo(TelemetryDO row) {
        return TelemetryLatestVo.builder()
                .key(row.getPropertyKey())
                .value(row.getValueNum() != null ? row.getValueNum() : row.getValueText())
                .occurredAt(row.getOccurredAt())
                .build();
    }

    private TelemetryPointVo toPointVo(TelemetryDO row) {
        return TelemetryPointVo.builder()
                .id(row.getId())
                .deviceId(row.getDeviceId())
                .key(row.getPropertyKey())
                .valueNum(row.getValueNum())
                .valueText(row.getValueText())
                .occurredAt(row.getOccurredAt())
                .build();
    }
}
