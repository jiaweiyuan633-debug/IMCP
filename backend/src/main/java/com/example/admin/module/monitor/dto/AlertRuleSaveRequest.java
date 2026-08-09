package com.example.admin.module.monitor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlertRuleSaveRequest {

    private Long id;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "监控指标不能为空")
    private String metric;

    @NotBlank(message = "比较符不能为空")
    private String operator;

    @NotNull(message = "阈值不能为空")
    @DecimalMin(value = "0", message = "阈值不能小于 0")
    private BigDecimal threshold;

    private Integer enabled;
    private String remark;
}
