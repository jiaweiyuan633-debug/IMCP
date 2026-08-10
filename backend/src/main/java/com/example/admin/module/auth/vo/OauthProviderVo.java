package com.example.admin.module.auth.vo;

import lombok.Builder;
import lombok.Data;

/** 登录页展示的第三方登录入口。 */
@Data
@Builder
public class OauthProviderVo {

    private String provider;
    private String label;
    private boolean enabled;
}
