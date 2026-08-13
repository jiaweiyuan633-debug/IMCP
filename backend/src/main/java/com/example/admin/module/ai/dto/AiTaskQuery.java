package com.example.admin.module.ai.dto;

import lombok.Data;

@Data
public class AiTaskQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String status;
    private String bizType;
    /** 失败原因分类（R4-1.20）：timeout / non_retryable / retries_exhausted。 */
    private String errorType;
}

