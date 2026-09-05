package cn.admin.scaffold.security;

import java.util.Set;

/**
 * 安全过滤器的端点白名单（method:path，均为 PathNormalizer 归一化口径）。
 *
 * <p>两个过滤器共用同一组常量避免口径漂移：
 * <ul>
 *   <li>{@link PasswordPolicyEnforcementFilter} 用 {@link #PASSWORD_POLICY_EXEMPT} 决定口令受限
 *       账号仍可访问的端点（仅改密/登出/刷新/自身信息）；</li>
 *   <li>{@link ApiPermAuthorizationFilter} 严格模式用 {@link #AUTH_SELF_SERVICE} 豁免「有意不登记
 *       资源权限」的个人自助端点，避免误杀。</li>
 * </ul>
 */
final class SecurityEndpointAllowlists {

    private SecurityEndpointAllowlists() {
    }

    /** 口令生命周期受限期间放行的端点：完成改密、退出、续期与自身信息查询即可，其余业务一律 403。 */
    static final Set<String> PASSWORD_POLICY_EXEMPT = Set.of(
            "PUT:/api/auth/password",
            "POST:/api/auth/logout",
            "POST:/api/auth/refresh",
            "GET:/api/auth/me");

    /** 个人自助端点（auth 模块下不登记资源权限、仅需认证的接口）：口令白名单 + 个人资料/两步验证等。 */
    static final Set<String> AUTH_SELF_SERVICE = Set.of(
            "PUT:/api/auth/password",
            "POST:/api/auth/logout",
            "POST:/api/auth/refresh",
            "GET:/api/auth/me",
            "PUT:/api/auth/profile",
            "GET:/api/auth/login-config",
            "GET:/api/auth/captcha",
            "GET:/api/auth/totp/status",
            "POST:/api/auth/totp/setup",
            "POST:/api/auth/totp/enable",
            "POST:/api/auth/totp/disable");
}
