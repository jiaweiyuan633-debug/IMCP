package com.example.admin.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_service_config")
public class AiServiceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    private String apiKey;
    private Integer timeoutSeconds;
    private Integer enabled;
}

