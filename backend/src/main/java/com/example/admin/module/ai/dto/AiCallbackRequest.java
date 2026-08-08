package com.example.admin.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiCallbackRequest {

    @JsonProperty("task_no")
    private String taskNo;

    @JsonProperty("biz_type")
    private String bizType;

    @JsonProperty("retry_count")
    private Integer retryCount;

    private String status;
    private Object result;
    private String error;
}
