package cn.admin.scaffold.module.device.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 遥测最新值：每个属性取 occurred_at 最大的一条。
 * value 为解析后的值（数值为 BigDecimal，文本/枚举为 String）。
 */
@Data
@Builder
public class TelemetryLatestVo {

    private String key;
    private Object value;
    private LocalDateTime occurredAt;
}
