package com.example.admin.module.monitor.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL 监控日志出参 VO，避免直接暴露 DO。
 */
@Data
@Builder
public class SqlLogVo {

    private Long id;
    private String sqlText;
    private String method;
    private Long durationMs;
    private Integer success;
    private String errorMsg;
    private LocalDateTime createdAt;
}
