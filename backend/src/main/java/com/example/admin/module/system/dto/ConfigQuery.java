package com.example.admin.module.system.dto;

import lombok.Data;

@Data
public class ConfigQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String configName;
    private String configKey;
}

