package cn.admin.scaffold.module.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OauthConfigVo {

    private Long id;
    private String provider;
    private String providerLabel;
    private String appId;
    private String appSecret;
    private String redirectUri;
    private String scope;
    private Integer enabled;
    private Integer sort;
    private String remark;
    private LocalDateTime createdAt;
}
