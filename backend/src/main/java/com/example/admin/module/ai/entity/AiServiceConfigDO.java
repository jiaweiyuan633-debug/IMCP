package com.example.admin.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_service_config")
public class AiServiceConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
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

