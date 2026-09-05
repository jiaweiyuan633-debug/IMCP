package cn.admin.scaffold.module.system.dto;

import cn.admin.scaffold.common.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserSaveRequest {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    @Pattern(
            regexp = PasswordPolicy.PATTERN,
            message = PasswordPolicy.MESSAGE)
    private String password;

    @Size(max = 255, message = "头像地址长度不能超过 255")
    private String avatar;

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phone;

    private Integer status;
    private Long deptId;
    private List<Long> roleIds;
    private List<Long> postIds;
}

