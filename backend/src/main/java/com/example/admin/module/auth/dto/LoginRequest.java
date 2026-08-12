package com.example.admin.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String captchaId;
    private String captchaCode;
    private String totpCode;

    /** 可选租户 ID：跨租户存在同名用户时用于精确定位（R1-1.7）；为空则按用户名查全部租户。 */
    private Long tenantId;
}

