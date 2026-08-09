package com.example.admin.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiConfigSaveRequest {

    @NotNull(message = "配置 ID 不能为空")
    private Long id;

    @NotBlank(message = "服务名称不能为空")
    private String name;

    @NotBlank(message = "服务地址不能为空")
    private String baseUrl;

    private String apiKey;
    private Integer timeoutSeconds;
    private Integer enabled;
    private Integer dailyLimit;
}

