package cn.admin.scaffold.module.ai.vo;

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
    /** 是否已配置 API Key（Key 本身不回显，仅标记存在性） */
    private boolean hasApiKey;
    private Integer timeoutSeconds;
    private Integer enabled;
    private Integer dailyLimit;
}

