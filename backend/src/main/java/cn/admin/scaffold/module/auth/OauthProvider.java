package cn.admin.scaffold.module.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 第三方 OAuth2 提供方（微信扫码 / GitHub / Gitee）。
 */
@Getter
@RequiredArgsConstructor
public enum OauthProvider {

    WECHAT("wechat", "微信扫码",
            "https://open.weixin.qq.com/connect/qrconnect",
            "https://api.weixin.qq.com/sns/oauth2/access_token",
            "https://api.weixin.qq.com/sns/userinfo"),
    GITHUB("github", "GitHub",
            "https://github.com/login/oauth/authorize",
            "https://github.com/login/oauth/access_token",
            "https://api.github.com/user"),
    GITEE("gitee", "Gitee",
            "https://gitee.com/oauth/authorize",
            "https://gitee.com/oauth/token",
            "https://gitee.com/api/v5/user");

    private final String code;
    private final String label;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String userInfoUrl;

    public static OauthProvider fromCode(String code) {
        for (OauthProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("不支持的第三方登录提供方: " + code);
    }
}
