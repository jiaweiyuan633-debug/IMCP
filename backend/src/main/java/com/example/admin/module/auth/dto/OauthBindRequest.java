package com.example.admin.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OauthBindRequest {

    /** 回调返回的一次性绑定凭证。 */
    @NotBlank(message = "绑定凭证不能为空")
    private String bindToken;

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
