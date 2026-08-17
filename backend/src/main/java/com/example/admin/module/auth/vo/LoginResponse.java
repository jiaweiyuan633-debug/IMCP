package com.example.admin.module.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private UserInfoVo user;
    /** 登录后是否必须修改密码（默认口令首登 / 密码过期；生产 forcePasswordChange 开启时生效）。 */
    private boolean mustChangePassword;
}
