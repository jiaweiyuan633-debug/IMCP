package com.example.admin.module.monitor.dto;

import lombok.Data;

@Data
public class JobQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String jobName;
    private String jobGroup;
    private Integer status;
}

