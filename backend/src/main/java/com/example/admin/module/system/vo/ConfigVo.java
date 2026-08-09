package com.example.admin.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConfigVo {

    private Long id;
    private String configName;
    private String configKey;
    private String configValue;
    private Integer configType;
    private String remark;
    private LocalDateTime createdAt;
}

