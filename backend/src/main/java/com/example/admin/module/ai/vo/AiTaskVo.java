package com.example.admin.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiTaskVo {

    private Long id;
    private String taskNo;
    private String bizType;
    private Long bizId;
    private String serviceCode;
    private String status;
    private String paramsJson;
    private String errorMsg;
    private String errorType;
    private Integer retryCount;
    private Integer maxRetry;
    private Integer timeoutSeconds;
    private String callbackUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AiTaskResultVo result;
}

