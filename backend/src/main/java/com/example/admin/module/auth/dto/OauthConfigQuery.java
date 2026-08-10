package com.example.admin.module.auth.dto;

import lombok.Data;

@Data
public class OauthConfigQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String provider;
    private Integer enabled;
}
