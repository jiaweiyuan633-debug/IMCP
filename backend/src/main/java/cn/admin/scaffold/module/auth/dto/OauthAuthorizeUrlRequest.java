package cn.admin.scaffold.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OauthAuthorizeUrlRequest {

    @NotBlank(message = "提供方不能为空")
    private String provider;

    /** true 表示从已登录状态发起绑定（自动绑定当前用户，跳转后直接返回）。 */
    private Boolean bindMode;
}
