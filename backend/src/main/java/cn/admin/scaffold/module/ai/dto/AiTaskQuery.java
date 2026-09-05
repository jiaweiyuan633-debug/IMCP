package cn.admin.scaffold.module.ai.dto;

import lombok.Data;

@Data
public class AiTaskQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String status;
    private String bizType;
    /** 失败原因分类：timeout / non_retryable / retries_exhausted。 */
    private String errorType;
}

