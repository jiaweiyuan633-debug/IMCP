package com.example.admin.module.notice.dto;

import lombok.Data;

@Data
public class ChannelConfigQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String channelType;
    private Integer status;
}
