package com.example.admin.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$",
            message = "新密码需 8-32 位，且同时包含字母和数字")
    private String newPassword;
}

