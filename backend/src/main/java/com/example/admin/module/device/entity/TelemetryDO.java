package com.example.admin.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 遥测实体（纯追加时序数据，不设 @Version / @TableLogic，按租户 + 设备归档）。
 */
@Data
@TableName("device_telemetry")
public class TelemetryDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private String propertyKey;
    private BigDecimal valueNum;
    private String valueText;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
