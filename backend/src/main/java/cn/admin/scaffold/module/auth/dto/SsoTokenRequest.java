package cn.admin.scaffold.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoTokenRequest {

    @NotBlank(message = "client_id 不能为空")
    private String clientId;

    @NotBlank(message = "client_secret 不能为空")
    private String clientSecret;

    @NotBlank(message = "授权码不能为空")
    private String code;

    private String grantType = "authorization_code";
}
