package com.example.admin.module.device.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceVo {

    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String location;
    private Integer sort;
    private Integer status;
    private String description;
    private LocalDateTime createdAt;
}
