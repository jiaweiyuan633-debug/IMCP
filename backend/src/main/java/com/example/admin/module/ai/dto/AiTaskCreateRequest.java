package com.example.admin.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class AiTaskCreateRequest {

    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    private Long bizId;
    private String serviceCode = "default";
    private Map<String, Object> params;
}

