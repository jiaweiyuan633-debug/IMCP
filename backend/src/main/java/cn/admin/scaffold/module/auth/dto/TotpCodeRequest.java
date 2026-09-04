package cn.admin.scaffold.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TotpCodeRequest {

    @NotBlank(message = "动态验证码不能为空")
    private String code;
}
