package cn.admin.scaffold.module.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TelemetryPoint {

    @NotBlank(message = "属性标识不能为空")
    @Size(max = 64, message = "属性标识长度不能超过 64")
    private String key;

    /** 属性值：数字 → value_num，其余（字符串/枚举等）→ value_text。 */
    private Object value;

    @NotNull(message = "采集时间不能为空")
    private LocalDateTime occurredAt;
}
