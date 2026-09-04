package cn.admin.scaffold.module.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OauthClientVo {

    private Long id;
    private String clientName;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope;
    private Integer enabled;
    private Integer sort;
    private String remark;
    private LocalDateTime createdAt;
}
