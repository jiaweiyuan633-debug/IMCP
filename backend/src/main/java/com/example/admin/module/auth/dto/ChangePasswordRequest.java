package com.example.admin.module.auth.dto;

import com.example.admin.common.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(
            regexp = PasswordPolicy.PATTERN,
            message = "新密码" + PasswordPolicy.MESSAGE)
    private String newPassword;
}

