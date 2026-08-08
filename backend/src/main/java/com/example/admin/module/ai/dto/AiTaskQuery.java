package com.example.admin.module.ai.dto;

import lombok.Data;

@Data
public class AiTaskQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String status;
    private String bizType;
}

