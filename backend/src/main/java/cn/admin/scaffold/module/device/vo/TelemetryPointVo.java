package cn.admin.scaffold.module.device.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 遥测时序点（history 分页行）：保留数值/文本分流后的原始字段。
 */
@Data
@Builder
public class TelemetryPointVo {

    private Long id;
    private Long deviceId;
    private String key;
    private BigDecimal valueNum;
    private String valueText;
    private LocalDateTime occurredAt;
}
