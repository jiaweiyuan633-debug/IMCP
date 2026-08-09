package com.example.admin.module.monitor.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志出参 VO，避免直接暴露 DO。
 */
@Data
@Builder
public class JobLogVo {

    private Long id;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String jobMessage;
    private Integer status;
    private String exceptionInfo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
