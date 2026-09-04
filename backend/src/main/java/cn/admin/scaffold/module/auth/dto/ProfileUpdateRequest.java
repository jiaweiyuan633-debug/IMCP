package cn.admin.scaffold.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Size(max = 255, message = "头像地址长度不能超过 255")
    private String avatar;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phone;
}

