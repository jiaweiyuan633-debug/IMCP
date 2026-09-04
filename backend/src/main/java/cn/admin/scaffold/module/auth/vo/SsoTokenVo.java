package cn.admin.scaffold.module.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoTokenVo {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String scope;
}
