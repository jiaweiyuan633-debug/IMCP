package com.example.admin.module.auth.dto;

import lombok.Data;

@Data
public class OauthClientQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String clientName;
    private Integer enabled;
}
