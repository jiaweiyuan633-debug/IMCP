package cn.admin.scaffold.module.device.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TelemetryReportRequest {

    /** 客户端生成的幂等上报 ID（不落库，仅作防重键）。 */
    @NotBlank(message = "遥测上报 ID 不能为空")
    private String telemetryId;

    @NotNull(message = "设备 ID 不能为空")
    private Long deviceId;

    @NotEmpty(message = "遥测数据点不能为空")
    @Valid
    private List<TelemetryPoint> points;
}
