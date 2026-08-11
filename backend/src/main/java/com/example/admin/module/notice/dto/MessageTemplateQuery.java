package com.example.admin.module.notice.dto;

import lombok.Data;

@Data
public class MessageTemplateQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String templateCode;
    private String templateName;
    private String messageType;
    private Integer status;
}
