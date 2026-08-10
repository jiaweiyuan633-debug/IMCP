package com.example.admin.module.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceSaveRequest {

    private Long id;

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 50, message = "设备编码长度不能超过 50")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过 100")
    private String deviceName;

    @Size(max = 50, message = "设备类型长度不能超过 50")
    private String deviceType;

    @Size(max = 200, message = "安装位置长度不能超过 200")
    private String location;

    private Integer sort;
    private Integer status;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
