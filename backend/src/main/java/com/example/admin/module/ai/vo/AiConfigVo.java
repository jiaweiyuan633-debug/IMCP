package com.example.admin.module.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiConfigVo {

    private Long id;
    private String code;
    private String provider;
    private String name;
    private String model;
    private String baseUrl;
    private String apiKey;
    private Integer timeoutSeconds;
    private Integer enabled;
    private Integer dailyLimit;
}

