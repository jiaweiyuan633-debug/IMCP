package com.example.admin.module.device.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TelemetryQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private Long deviceId;
    private String property;
    private LocalDateTime start;
    private LocalDateTime end;
}
