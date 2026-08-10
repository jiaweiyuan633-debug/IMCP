package com.example.admin.module.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OauthBindingVo {

    private String provider;
    private String providerLabel;
    private String openId;
    private String nickname;
    private String avatar;
    private LocalDateTime createdAt;
}
