package com.example.admin.module.device.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceStatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
